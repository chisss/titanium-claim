package com.titanium.claim.application.orchestration.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 理算编排器测试（CLAIM-2 身故给付精算编排）
 * <p>
 * 验证 {@link ClaimSettlementOrchestrator}：Port 取保单基本保额 → {@code BenefitCalculation} 精算分配
 * （金额不再由调用方透传）→ 装配身故证据与命令发送；保单非生效/基本保额缺失时以领域异常中断。
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

    @Test
    @DisplayName("生效保单：取基本保额精算给付并按份额分配后发送命令")
    void shouldSettleByBasicSumInsured() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(new PolicyInfo("POL-1", "ACTIVE", new BigDecimal("500000")));

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
    @DisplayName("保单非生效状态抛保单未生效异常，不发命令")
    void shouldRejectInactivePolicy() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(new PolicyInfo("POL-1", "EXPIRED", new BigDecimal("500000")));

        assertThrows(PolicyNotActiveException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", validRequest()));
        verify(commandGateway, org.mockito.Mockito.never()).sendAndWait(any());
    }

    @Test
    @DisplayName("保单不存在抛保单未生效异常")
    void shouldRejectMissingPolicy() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(null);

        assertThrows(PolicyNotActiveException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", validRequest()));
        verify(commandGateway, org.mockito.Mockito.never()).sendAndWait(any());
    }

    @Test
    @DisplayName("基本保额缺失抛金额无效错误码，不发命令")
    void shouldRejectMissingBasicSumInsured() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(new PolicyInfo("POL-1", "ACTIVE", null));

        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", validRequest()));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_AMOUNT_INVALID.getCode(), ex.getErrorCode());
        verify(commandGateway, org.mockito.Mockito.never()).sendAndWait(any());
    }

    @Test
    @DisplayName("受益人比例之和不守恒抛份额不匹配错误码，不发命令")
    void shouldRejectUnbalancedShares() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(new PolicyInfo("POL-1", "ACTIVE", new BigDecimal("500000")));
        SettleDeathBenefitRequest request = validRequest();
        request.getShares().get(1).setBenefitRatio(new BigDecimal("0.3"));

        BenefitCalculationException ex = assertThrows(BenefitCalculationException.class,
                () -> orchestrator.settleDeathBenefit("CLAIM-1", request));
        assertEquals(ClaimErrorCode.CLAIM_BENEFIT_SHARE_MISMATCH.getCode(), ex.getErrorCode());
        verify(commandGateway, org.mockito.Mockito.never()).sendAndWait(any());
    }

    @Test
    @DisplayName("Port 取数携带真实租户ID")
    void shouldPassRealTenantIdToPort() {
        when(policyServicePort.getPolicy(eq("POL-1"), eq("T-1")))
                .thenReturn(new PolicyInfo("POL-1", "ACTIVE", new BigDecimal("500000")));

        orchestrator.settleDeathBenefit("CLAIM-1", validRequest());

        verify(policyServicePort).getPolicy("POL-1", "T-1");
    }
}
