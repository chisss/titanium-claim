package com.titanium.claim.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.application.model.assessment.ReimbursementSettlementRequest;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.enums.config.SettlementChannel;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.view.ClaimView;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;
import com.titanium.claim.repository.ClaimPayoutRuleRepository;
import com.titanium.claim.service.ReimbursementAdjustmentService;
import com.titanium.claim.service.impl.ReimbursementAdjustmentServiceImpl;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.exception.DomainException;

/**
 * 报销理算查询服务测试（application 读入口编排）
 * <p>
 * 验证「取数 → 调领域服务」编排：赔付规则租户覆盖优先、回落平台默认；医院名查台账；
 * 规则缺失抛 {@code CLAIM_CONFIG_NOT_FOUND}；比例裁决与金额计算委托领域服务。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("报销理算查询服务")
class ReimbursementAdjustmentQueryServiceTest {

    private static final String TENANT_ID = "tenant-001";

    @Mock
    private ClaimPayoutRuleRepository     payoutRuleRepository;
    @Mock
    private ClaimHospitalNetworkRepository hospitalRepository;
    @Mock
    private ClaimViewRepository           claimViewRepository;

    private ReimbursementAdjustmentQueryService queryService;

    @BeforeEach
    void setUp() {
        RequestContextHolder.setTenantId(TENANT_ID);
        ReimbursementAdjustmentService adjustmentService = new ReimbursementAdjustmentServiceImpl();
        queryService = new ReimbursementAdjustmentQueryService(payoutRuleRepository, hospitalRepository,
                claimViewRepository, adjustmentService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    private ReimbursementSettlementRequest request(String hospitalName) {
        ReimbursementSettlementRequest request = new ReimbursementSettlementRequest();
        request.setInsuranceLine("PET");
        request.setClaimType("PET_MEDICAL");
        request.setHospitalName(hospitalName);
        request.setEligibleExpense(new BigDecimal("1000.00"));
        return request;
    }

    private ClaimPayoutRule rule() {
        return ClaimPayoutRule.create("rule-1", TENANT_ID, "PET", "PET_MEDICAL",
                new BigDecimal("100.00"), 60, null, null, Map.of("NON_DESIGNATED", 40), null, List.of());
    }

    @Test
    @DisplayName("租户规则命中 + 定点医院 → 套台账比例")
    void shouldUseTenantRuleAndDesignatedHospital() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.of(rule()));
        when(hospitalRepository.findByName(TENANT_ID, "爱宠宠物医院")).thenReturn(Optional.of(
                ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "爱宠宠物医院", "二级",
                        HospitalAgreementStatus.ACTIVE, 80, true, "朝阳区", "13800000000")));

        ReimbursementAdjustmentResult result = queryService.adjust(request("爱宠宠物医院"));

        assertThat(result.settlementChannel()).isEqualTo(SettlementChannel.DESIGNATED);
        assertThat(result.payoutRatioUsed()).isEqualTo(80);
        assertThat(result.calculation().payableAmount()).isEqualByComparingTo("720.00");
    }

    @Test
    @DisplayName("租户规则未命中 → 回落平台默认模板")
    void shouldFallbackToPlatformDefault() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.empty());
        when(payoutRuleRepository.findPlatformDefault("PET", "PET_MEDICAL")).thenReturn(Optional.of(rule()));

        ReimbursementAdjustmentResult result = queryService.adjust(request(null));

        assertThat(result.settlementChannel()).isEqualTo(SettlementChannel.NON_DESIGNATED);
        assertThat(result.payoutRatioUsed()).isEqualTo(40);
    }

    @Test
    @DisplayName("规则缺失（租户+平台均无）→ 抛 CLAIM_CONFIG_NOT_FOUND")
    void shouldThrowWhenRuleMissing() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.empty());
        when(payoutRuleRepository.findPlatformDefault("PET", "PET_MEDICAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.adjust(request(null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("赔付规则不存在");
    }

    @Test
    @DisplayName("医院名为空 → 不查台账，直接非定点")
    void shouldSkipHospitalLookupWhenNameBlank() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.of(rule()));

        ReimbursementAdjustmentResult result = queryService.adjust(request(null));

        verify(hospitalRepository, never()).findByName(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(result.settlementChannel()).isEqualTo(SettlementChannel.NON_DESIGNATED);
    }

    // ==================== 年度封顶取数（G12/g12-04b、AC-04） ====================

    /**
     * 配置了年度限额的赔付规则（其余参数与 {@link #rule()} 相同）。
     */
    private ClaimPayoutRule ruleWithAnnualLimit() {
        return ClaimPayoutRule.create("rule-1", TENANT_ID, "PET", "PET_MEDICAL",
                new BigDecimal("100.00"), 60, null, new BigDecimal("1200.00"),
                Map.of("NON_DESIGNATED", 40), null, List.of());
    }

    /**
     * 携带年度累计取数上下文的案件读模型（保单/标的/出险日齐备）。
     */
    private ClaimView claimView(String claimId, String policyId, String subjectId, LocalDateTime incidentDate) {
        ClaimView view = new ClaimView();
        view.setClaimId(claimId);
        view.setPolicyId(policyId);
        view.setSubjectId(subjectId);
        view.setIncidentDate(incidentDate);
        return view;
    }

    @Test
    @DisplayName("年度封顶取数：按案件定自然年累计并压减应付（公式 360，年额 1200 累计 1000 ⇒ 200）")
    void shouldResolveAnnualAccumulatedAndCap() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.of(ruleWithAnnualLimit()));
        when(claimViewRepository.findByClaimIdAndTenantId("claim-1", TENANT_ID)).thenReturn(Optional.of(
                claimView("claim-1", "policy-1", "subject-1", LocalDateTime.of(2026, 5, 20, 10, 0))));
        when(claimViewRepository.sumSettledAmountByAnnualContext(eq(TENANT_ID), eq("policy-1"), eq("subject-1"),
                eq(LocalDateTime.of(2026, 1, 1, 0, 0)), eq(LocalDateTime.of(2027, 1, 1, 0, 0)),
                eq(List.of(ClaimStatus.PAID, ClaimStatus.CLOSED)), eq("claim-1")))
                .thenReturn(new BigDecimal("1000.00"));

        ReimbursementSettlementRequest request = request(null);
        request.setClaimId("claim-1");
        ReimbursementAdjustmentResult result = queryService.adjust(request);

        // 单次公式：(1000−100)×40% = 360.00；年度剩余 1200−1000 = 200 ⇒ 压减至 200.00
        assertThat(result.calculation().payableAmount()).isEqualByComparingTo("200.00");
        assertThat(result.calculation().cappedByAnnualLimit()).isTrue();
    }

    @Test
    @DisplayName("未携带案件ID：单次公式口径（360.00），不触碰案件读模型")
    void shouldSkipAnnualLookupWithoutClaimId() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.of(ruleWithAnnualLimit()));

        ReimbursementAdjustmentResult result = queryService.adjust(request(null));
        // claimId 为空 ⇒ 试算兼容路径，不得查询读模型
        assertThat(result.calculation().payableAmount()).isEqualByComparingTo("360.00");
        verifyNoInteractions(claimViewRepository);
    }

    @Test
    @DisplayName("规则未配年度限额：取数层短路，不触碰案件读模型")
    void shouldSkipAnnualLookupWhenRuleHasNoAnnualLimit() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.of(rule()));
        when(hospitalRepository.findByName(TENANT_ID, "爱宠宠物医院")).thenReturn(Optional.of(
                ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "爱宠宠物医院", "二级",
                        HospitalAgreementStatus.ACTIVE, 80, true, "朝阳区", "13800000000")));

        ReimbursementSettlementRequest request = request("爱宠宠物医院");
        request.setClaimId("claim-1");
        ReimbursementAdjustmentResult result = queryService.adjust(request);

        assertThat(result.calculation().payableAmount()).isEqualByComparingTo("720.00");
        verifyNoInteractions(claimViewRepository);
    }

    @Test
    @DisplayName("案件ID 非空而案件不存在：fail-closed 抛 CLAIM_NOT_EXIST（不按零累计放行）")
    void shouldThrowWhenClaimNotFoundForAnnualContext() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.of(ruleWithAnnualLimit()));
        when(claimViewRepository.findByClaimIdAndTenantId("missing", TENANT_ID)).thenReturn(Optional.empty());

        ReimbursementSettlementRequest request = request(null);
        request.setClaimId("missing");
        assertThatThrownBy(() -> queryService.adjust(request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("理赔案件不存在");
    }
}
