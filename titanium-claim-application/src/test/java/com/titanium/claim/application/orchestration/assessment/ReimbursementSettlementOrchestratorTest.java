package com.titanium.claim.application.orchestration.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.application.model.settlement.SettleReimbursementRequest;
import com.titanium.claim.application.query.ReimbursementAdjustmentQueryService;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;
import com.titanium.claim.repository.ClaimPayoutRuleRepository;
import com.titanium.claim.service.ReimbursementAdjustmentService;
import com.titanium.claim.service.impl.ReimbursementAdjustmentServiceImpl;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 报销理算结算编排器测试（m16-1905，健康险/宠物险）
 * <p>
 * 验证「理算 → 结算」接线的核心性质：<b>命令里的赔付金额由系统精算得出，不来自调用方</b>。
 * 测试用真实的 {@link ReimbursementAdjustmentServiceImpl} 与真实读入口（只 mock 仓储与租户上下文），
 * 故断言的金额是「合规费用 × 赔付规则」的完整推导结果，而非桩值——这正是本任务要固化的口径：
 * 结算金额与试算金额同源（同一条取数 + 同一个领域服务），不存在第二套来源。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("报销理算结算编排器")
class ReimbursementSettlementOrchestratorTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String CLAIM_ID  = "CLAIM-RB-1";

    @Mock
    private ClaimPayoutRuleRepository      payoutRuleRepository;
    @Mock
    private ClaimHospitalNetworkRepository hospitalRepository;
    @Mock
    private TenantContext                  tenantContext;
    @Mock
    private CommandGateway                 commandGateway;

    private ReimbursementSettlementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
        ReimbursementAdjustmentService adjustmentService = new ReimbursementAdjustmentServiceImpl();
        ReimbursementAdjustmentQueryService queryService = new ReimbursementAdjustmentQueryService(
                payoutRuleRepository, hospitalRepository, adjustmentService, tenantContext);
        orchestrator = new ReimbursementSettlementOrchestrator(queryService, commandGateway);
    }

    /** 结算入参：合规费用 1000 元，给付方式银行转账——注意模型里<b>没有金额字段</b> */
    private SettleReimbursementRequest request() {
        SettleReimbursementRequest request = new SettleReimbursementRequest();
        request.setInsuranceLine("PET");
        request.setClaimType("PET_MEDICAL");
        request.setHospitalName("爱宠宠物医院");
        request.setEligibleExpense(new BigDecimal("1000.00"));
        request.setPayoutMethod("BANK_TRANSFER");
        request.setPayeeAccount("6222****1234");
        request.setConclusion("核赔通过");
        return request;
    }

    /** 赔付规则：免赔 100、基础比例 60%、非定点档位 40%、不限单次限额 */
    private ClaimPayoutRule rule() {
        return ClaimPayoutRule.create("rule-1", TENANT_ID, "PET", "PET_MEDICAL",
                new BigDecimal("100.00"), 60, null, null, Map.of("NON_DESIGNATED", 40), List.of());
    }

    private void givenRulePresent() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.of(rule()));
    }

    @Test
    @DisplayName("理算金额进入结算命令：(1000−100)×定点80% = 720，非调用方传入")
    void shouldSettleWithAdjustedAmountForDesignatedHospital() {
        givenRulePresent();
        when(hospitalRepository.findByName(TENANT_ID, "爱宠宠物医院")).thenReturn(Optional.of(
                ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "爱宠宠物医院", "二级",
                        HospitalAgreementStatus.ACTIVE, 80, true, "朝阳区", "13800000000")));

        orchestrator.settle(CLAIM_ID, request());

        SettleClaimCommand command = captureSettleCommand();
        assertThat(command.settledAmount()).isEqualByComparingTo("720.00");
        assertThat(command.claimId().value()).isEqualTo(CLAIM_ID);
    }

    @Test
    @DisplayName("非定点（医院不在台账）→ 套非定点档位 40%：(1000−100)×40% = 360")
    void shouldSettleWithNonDesignatedRatioWhenHospitalNotInLedger() {
        givenRulePresent();
        when(hospitalRepository.findByName(TENANT_ID, "爱宠宠物医院")).thenReturn(Optional.empty());

        orchestrator.settle(CLAIM_ID, request());

        assertThat(captureSettleCommand().settledAmount()).isEqualByComparingTo("360.00");
    }

    @Test
    @DisplayName("给付参数透传：给付方式 code 还原为枚举，收款账户/结论原样带入")
    void shouldPassPayoutParametersThrough() {
        givenRulePresent();

        orchestrator.settle(CLAIM_ID, request());

        SettleClaimCommand command = captureSettleCommand();
        assertThat(command.payoutMethod()).isEqualTo(ClaimEnum.PayoutMethod.BANK_TRANSFER);
        assertThat(command.payeeAccount()).isEqualTo("6222****1234");
        assertThat(command.conclusion()).isEqualTo("核赔通过");
    }

    @Test
    @DisplayName("赔付规则缺失 → 拒绝且不发任何命令（不得无依据结算）")
    void shouldNotSendCommandWhenPayoutRuleMissing() {
        when(payoutRuleRepository.findByBusinessKey(TENANT_ID, "PET", "PET_MEDICAL"))
                .thenReturn(Optional.empty());
        when(payoutRuleRepository.findPlatformDefault("PET", "PET_MEDICAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.settle(CLAIM_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("赔付规则不存在");

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("合规费用非正 → 拒绝且不发任何命令（由理算入参值对象的不变量挡住）")
    void shouldNotSendCommandWhenEligibleExpenseNotPositive() {
        givenRulePresent();
        SettleReimbursementRequest request = request();
        request.setEligibleExpense(BigDecimal.ZERO);

        assertThatThrownBy(() -> orchestrator.settle(CLAIM_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合规费用必须大于0");

        verify(commandGateway, never()).sendAndWait(any());
    }

    private SettleClaimCommand captureSettleCommand() {
        ArgumentCaptor<SettleClaimCommand> captor = ArgumentCaptor.forClass(SettleClaimCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        return captor.getValue();
    }
}
