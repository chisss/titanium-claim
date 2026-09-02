package com.titanium.claim.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.application.model.assessment.ReimbursementSettlementRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.enums.config.SettlementChannel;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;
import com.titanium.claim.repository.ClaimPayoutRuleRepository;
import com.titanium.claim.service.ReimbursementAdjustmentService;
import com.titanium.claim.service.impl.ReimbursementAdjustmentServiceImpl;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;

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
    private TenantContext                 tenantContext;

    private ReimbursementAdjustmentQueryService queryService;

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
        ReimbursementAdjustmentService adjustmentService = new ReimbursementAdjustmentServiceImpl();
        queryService = new ReimbursementAdjustmentQueryService(payoutRuleRepository, hospitalRepository,
                adjustmentService, tenantContext);
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
                new BigDecimal("100.00"), 60, null, null, Map.of("NON_DESIGNATED", 40), List.of());
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
                .isInstanceOf(BusinessException.class)
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
}
