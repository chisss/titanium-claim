package com.titanium.claim.application.orchestration.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.application.model.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.command.SettleDeathBenefitCommand;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.common.exception.BenefitCalculationException;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.port.policy.BeneficiaryInfo;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 理算编排器测试（CLAIM-2 身故给付精算编排 + CLAIM-4 受益人核验）
 * <p>
 * 验证 {@link ClaimSettlementOrchestrator}：受益人主数据核验（拒绝未知受益人、按顺位排序分配）→
 * Port 取保单基本保额 → {@code BenefitCalculation} 精算分配（金额不再由调用方透传）→
 * 装配身故证据与命令发送；保单非生效/基本保额缺失/受益人非法时以领域异常中断。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimSettlementOrchestratorTest {

    @Mock
    private PolicyServicePort policyServicePort;
    @Mock
    private CommandGateway    commandGateway;

    private ClaimSettlementOrchestrator orchestrator;
    private TenantContext              tenantContext;

    @BeforeEach
    void setUp() {
        tenantContext = new TenantContext();
        tenantContext.setCurrentTenantId("T-1");
        orchestrator = new ClaimSettlementOrchestrator(policyServicePort, tenantContext, commandGateway);
    }

    private SettleDeathBenefitRequest validRequest() {
        SettleDeathBenefitRequest request = new SettleDeathBenefitRequest();
        request.setPolicyId("POL-1");
        request.setDeathCertificateNo("DC-001");
        request.setDeathDate(LocalDateTime.now().minusDays(1));
        request.setDeathCause("疾病身故");
        request.setHouseholdCancelled(true);
        request.setBeneficiaryProofNo("BP-001");
        request.setPayoutMethod(ClaimEnum.PayoutMethod.BANK_TRANSFER.getCode());
        request.setConclusion("身故给付核准");
        SettleDeathBenefitRequest.BeneficiaryShare s1 = new SettleDeathBenefitRequest.BeneficiaryShare();
        s1.setBeneficiaryId("B-1");
        s1.setBeneficiaryName("配偶");
        s1.setBenefitRatio(new BigDecimal("0.6"));
        SettleDeathBenefitRequest.BeneficiaryShare s2 = new SettleDeathBenefitRequest.BeneficiaryShare();
        s2.setBeneficiaryId("B-2");
        s2.setBeneficiaryName("子女");
        s2.setBenefitRatio(new BigDecimal("0.4"));
        request.setShares(List.of(s1, s2));
        return request;
    }

    private PolicyInfo activePolicy() {
        return new PolicyInfo("POL-1", "ACTIVE", new BigDecimal("500000"), LocalDateTime.now().minusDays(30));
    }

    private List<BeneficiaryInfo> masterBeneficiaries() {
        return List.of(
                new BeneficiaryInfo("B-1", "配偶", "DESIGNATED", 1, new BigDecimal("60")),
                new BeneficiaryInfo("B-2", "子女", "DESIGNATED", 2, new BigDecimal("40")));
    }

    @Test
    @DisplayName("生效保单：受益人核验通过，取基本保额精算给付并按份额分配后发送命令")
    void shouldSettleByBasicSumInsured() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(activePolicy());
        when(policyServicePort.fetchBeneficiaries("POL-1", "T-1")).thenReturn(masterBeneficiaries());

        orchestrator.settleDeathBenefit("CLAIM-1", validRequest());

        ArgumentCaptor<SettleDeathBenefitCommand> captor = ArgumentCaptor.forClass(SettleDeathBenefitCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        SettleDeathBenefitCommand command = captor.getValue();
        assertEquals("CLAIM-1", command.claimId().value());
        assertEquals(BenefitSource.BASIC_SUM_INSURED, command.benefitCalculation().source());
        assertEquals(new BigDecimal("500000"), command.benefitCalculation().totalBenefit());
        assertEquals(0, command.benefitCalculation().payoutOf("B-1").compareTo(new BigDecimal("300000")));
        assertEquals(0, command.benefitCalculation().payoutOf("B-2").compareTo(new BigDecimal("200000")));
        assertEquals("DC-001", command.evidence().deathCertificateNo());
        assertEquals(ClaimEnum.PayoutMethod.BANK_TRANSFER, command.payoutMethod());
    }

    @Test
    @DisplayName("请求受益人顺序与主数据顺位不一致时按顺位重排，尾差调整至末顺位")
    void shouldReorderSharesByMasterOrder() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(activePolicy());
        when(policyServicePort.fetchBeneficiaries("POL-1", "T-1")).thenReturn(List.of(
                new BeneficiaryInfo("B-1", "配偶", "DESIGNATED", 1, new BigDecimal("40")),
                new BeneficiaryInfo("B-2", "子女", "DESIGNATED", 2, new BigDecimal("30")),
                new BeneficiaryInfo("B-3", "父母", "DESIGNATED", 3, new BigDecimal("30"))));
        SettleDeathBenefitRequest request = validRequest();
        // 请求顺序 B-3 → B-1 → B-2（与主数据顺位 1,2,3 不一致），各 1/3
        SettleDeathBenefitRequest.BeneficiaryShare s1 = new SettleDeathBenefitRequest.BeneficiaryShare();
        s1.setBeneficiaryId("B-3");
        s1.setBeneficiaryName("父母");
        s1.setBenefitRatio(new BigDecimal("0.3333"));
        SettleDeathBenefitRequest.BeneficiaryShare s2 = new SettleDeathBenefitRequest.BeneficiaryShare();
        s2.setBeneficiaryId("B-1");
        s2.setBeneficiaryName("配偶");
        s2.setBenefitRatio(new BigDecimal("0.3333"));
        SettleDeathBenefitRequest.BeneficiaryShare s3 = new SettleDeathBenefitRequest.BeneficiaryShare();
        s3.setBeneficiaryId("B-2");
        s3.setBeneficiaryName("子女");
        s3.setBenefitRatio(new BigDecimal("0.3334"));
        request.setShares(List.of(s1, s2, s3));

        orchestrator.settleDeathBenefit("CLAIM-1", request);

        ArgumentCaptor<SettleDeathBenefitCommand> captor = ArgumentCaptor.forClass(SettleDeathBenefitCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        SettleDeathBenefitCommand command = captor.getValue();
        // 按顺位重排后 B-1(1) → B-2(2) → B-3(3)，尾差调整归最后一位 B-3
        assertEquals(0, command.benefitCalculation().payoutOf("B-1").compareTo(new BigDecimal("166650.00")));
        assertEquals(0, command.benefitCalculation().payoutOf("B-2").compareTo(new BigDecimal("166700.00")));
        assertEquals(0, command.benefitCalculation().payoutOf("B-3").compareTo(new BigDecimal("166650.00")));
    }

    @Test
    @DisplayName("请求受益人不在保单受益人主数据中抛受益人非法错误码，不发命令")
    void shouldRejectUnknownBeneficiary() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(activePolicy());
        when(policyServicePort.fetchBeneficiaries("POL-1", "T-1")).thenReturn(masterBeneficiaries());
        SettleDeathBenefitRequest request = validRequest();
        request.getShares().get(1).setBeneficiaryId("B-99");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", request));
        assertEquals(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID.getCode(), ex.getErrorCode());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("保单受益人主数据为空抛受益人非法错误码，不发命令")
    void shouldRejectEmptyMasterBeneficiaries() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(activePolicy());
        when(policyServicePort.fetchBeneficiaries("POL-1", "T-1")).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", validRequest()));
        assertEquals(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID.getCode(), ex.getErrorCode());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("身故给付未指定受益人抛受益人非法错误码，不发命令")
    void shouldRejectMissingShares() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(activePolicy());
        when(policyServicePort.fetchBeneficiaries("POL-1", "T-1")).thenReturn(masterBeneficiaries());
        SettleDeathBenefitRequest request = validRequest();
        request.setShares(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", request));
        assertEquals(ClaimErrorCode.CLAIM_BENEFICIARY_INVALID.getCode(), ex.getErrorCode());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("保单非生效状态抛保单未生效异常，不发命令")
    void shouldRejectInactivePolicy() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(new PolicyInfo("POL-1", "EXPIRED", new BigDecimal("500000"), null));

        assertThrows(PolicyNotActiveException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", validRequest()));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("保单不存在抛保单未生效异常")
    void shouldRejectMissingPolicy() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(null);

        assertThrows(PolicyNotActiveException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", validRequest()));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("基本保额缺失抛金额无效错误码，不发命令")
    void shouldRejectMissingBasicSumInsured() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(new PolicyInfo("POL-1", "ACTIVE", null, LocalDateTime.now().minusDays(30)));

        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", validRequest()));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID.getCode(), ex.getErrorCode());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("受益人比例之和不守恒抛份额不匹配错误码，不发命令")
    void shouldRejectUnbalancedShares() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(activePolicy());
        when(policyServicePort.fetchBeneficiaries("POL-1", "T-1")).thenReturn(masterBeneficiaries());
        SettleDeathBenefitRequest request = validRequest();
        request.getShares().get(1).setBenefitRatio(new BigDecimal("0.3"));

        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", request));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISMATCH.getCode(), ex.getErrorCode());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("Port 取数携带真实租户ID")
    void shouldPassRealTenantIdToPort() {
        when(policyServicePort.getPolicy(eq("POL-1"), eq("T-1"))).thenReturn(activePolicy());
        when(policyServicePort.fetchBeneficiaries(eq("POL-1"), eq("T-1"))).thenReturn(masterBeneficiaries());

        orchestrator.settleDeathBenefit("CLAIM-1", validRequest());

        verify(policyServicePort).getPolicy("POL-1", "T-1");
        verify(policyServicePort).fetchBeneficiaries("POL-1", "T-1");
    }
}
