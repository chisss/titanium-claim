package com.titanium.claim.application.orchestration.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.aggregate.ClaimQuickPayRule;
import com.titanium.claim.command.ChangeClaimStatusCommand;
import com.titanium.claim.command.FlagClaimAlertCommand;
import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.enums.AlertType;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.view.ClaimView;
import com.titanium.claim.repository.ClaimQuickPayRuleRepository;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 快赔自动核赔编排器测试（dev-012c，产品文档 §2.10 小额快赔通道判据矩阵）
 * <p>
 * 验证 {@link QuickPayOrchestrator}：规则匹配（租户覆盖 &gt; 平台默认）与判据
 * 「规则开启 + 金额 ≤ 阈值 + 状态 PROCESSING + 无欺诈警示标记」；全过自动核赔三命令
 * （状态 APPROVED → 结算 → 打快赔统计标记）；任一不满足以领域异常中断且不产生任何命令。
 * 快赔标记是统计口径标记不阻断快赔（防自我阻断）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class QuickPayOrchestratorTest {

    @Mock
    private ClaimViewRepository         claimViewRepository;
    @Mock
    private ClaimQuickPayRuleRepository quickPayRuleRepository;
    @Mock
    private CommandGateway              commandGateway;

    private QuickPayOrchestrator orchestrator;

    private static final String CLAIM_ID  = "CLAIM-QP-1";
    private static final String TENANT_ID = "T-1";

    @BeforeEach
    void setUp() {
        orchestrator = new QuickPayOrchestrator(claimViewRepository, quickPayRuleRepository, commandGateway);
    }

    private ClaimView view(String status, BigDecimal amount, String alertFlags) {
        ClaimView view = new ClaimView();
        view.setClaimId(CLAIM_ID);
        view.setClaimType(ClaimEnum.ClaimType.MEDICAL);
        view.setStatus(ClaimStatus.fromCode(status));
        view.setClaimAmount(amount);
        view.setAlertFlags(alertFlags);
        return view;
    }

    /** 已定损案件的读模型形态：多一个按定损核定的应赔金额（= 聚合 {@code assessedPayableAmount()}） */
    private ClaimView assessedView(BigDecimal claimAmount, BigDecimal assessedPayableAmount) {
        ClaimView view = view("PROCESSING", claimAmount, null);
        view.setAssessedPayableAmount(assessedPayableAmount);
        return view;
    }

    private ClaimQuickPayRule enabledRule() {
        return ClaimQuickPayRule.create("RULE-1", TENANT_ID, "MEDICAL", true, new BigDecimal("5000"));
    }

    @Test
    @DisplayName("判据全过 → 自动核赔：状态 APPROVED → 结算 → 打快赔标记（顺序断言）")
    void shouldAutoSettleWhenAllCriteriaPass() {
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("PROCESSING", new BigDecimal("3000"), null)));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL"))
                .thenReturn(Optional.of(enabledRule()));

        orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID);

        InOrder inOrder = Mockito.inOrder(commandGateway);
        ArgumentCaptor<ChangeClaimStatusCommand> statusCaptor =
                ArgumentCaptor.forClass(ChangeClaimStatusCommand.class);
        ArgumentCaptor<SettleClaimCommand> settleCaptor = ArgumentCaptor.forClass(SettleClaimCommand.class);
        ArgumentCaptor<FlagClaimAlertCommand> flagCaptor = ArgumentCaptor.forClass(FlagClaimAlertCommand.class);
        inOrder.verify(commandGateway).sendAndWait(statusCaptor.capture());
        inOrder.verify(commandGateway).sendAndWait(settleCaptor.capture());
        inOrder.verify(commandGateway).sendAndWait(flagCaptor.capture());
        assertEquals(ClaimStatus.APPROVED, statusCaptor.getValue().newStatus());
        // 未定损：调用方须给金额，取读模型申报金额（聚合无定损依据时用该值）
        assertEquals(0, settleCaptor.getValue().settledAmount().compareTo(new BigDecimal("3000")));
        assertEquals(ClaimEnum.PayoutMethod.BANK_TRANSFER, settleCaptor.getValue().payoutMethod());
        assertEquals(ClaimConstants.QUICK_PAY_CONCLUSION, settleCaptor.getValue().conclusion());
        assertEquals(AlertType.QUICK_PAY, flagCaptor.getValue().flags().get(0).type());
        assertEquals(ClaimConstants.AlertRule.RULE_QUICK_PAY, flagCaptor.getValue().flags().get(0).ruleCode());
    }

    @Test
    @DisplayName("🔴 已定损案件 → 结算不传金额：交聚合以定损核定额裁决，不透传申报金额")
    void shouldNotProvideAmountWhenLossAssessed() {
        // 申报金额 3000 ≠ 定损核定额 2400：原实现透传 3000 会被聚合判定「与核定额不等」而拒绝
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(assessedView(new BigDecimal("3000"), new BigDecimal("2400"))));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL"))
                .thenReturn(Optional.of(enabledRule()));

        orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID);

        ArgumentCaptor<SettleClaimCommand> settleCaptor = ArgumentCaptor.forClass(SettleClaimCommand.class);
        verify(commandGateway, Mockito.atLeastOnce()).sendAndWait(settleCaptor.capture());
        assertNull(settleCaptor.getValue().settledAmount(),
                "已定损案件不得指定金额——金额权威在聚合（定损核定额），透传申报金额会因不等而被拒、令快赔断链");
    }

    @Test
    @DisplayName("规则禁用 → 中断，不发命令")
    void shouldRejectWhenRuleDisabled() {
        ClaimQuickPayRule disabled = ClaimQuickPayRule.create("RULE-1", TENANT_ID, "MEDICAL", false,
                new BigDecimal("5000"));
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("PROCESSING", new BigDecimal("3000"), null)));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL"))
                .thenReturn(Optional.of(disabled));

        assertThrows(BusinessException.class, () -> orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("金额超阈值 → 中断")
    void shouldRejectWhenExceedingThreshold() {
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("PROCESSING", new BigDecimal("5000.01"), null)));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL"))
                .thenReturn(Optional.of(enabledRule()));

        assertThrows(BusinessException.class, () -> orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("状态非 PROCESSING → 中断")
    void shouldRejectWhenNotProcessing() {
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("APPROVED", new BigDecimal("3000"), null)));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL"))
                .thenReturn(Optional.of(enabledRule()));

        assertThrows(BusinessException.class, () -> orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("存在欺诈警示（延迟报案）→ 中断")
    void shouldRejectWhenFraudAlertPresent() {
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("PROCESSING", new BigDecimal("3000"), "LATE_REPORT")));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL"))
                .thenReturn(Optional.of(enabledRule()));

        assertThrows(BusinessException.class, () -> orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("仅有快赔标记（统计口径）→ 不阻断（防自我阻断）")
    void shouldNotBlockByQuickPayFlagItself() {
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("PROCESSING", new BigDecimal("3000"), "QUICK_PAY")));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL"))
                .thenReturn(Optional.of(enabledRule()));

        orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID);

        verify(commandGateway, Mockito.times(3)).sendAndWait(any());
    }

    @Test
    @DisplayName("规则缺失（租户+平台均无）→ CLAIM_CONFIG_NOT_FOUND")
    void shouldThrowConfigNotFoundWhenNoRule() {
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("PROCESSING", new BigDecimal("3000"), null)));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL")).thenReturn(Optional.empty());
        when(quickPayRuleRepository.findPlatformDefault("MEDICAL")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID));
        assertEquals(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("租户无规则 → 回退平台默认规则")
    void shouldFallbackToPlatformDefault() {
        ClaimQuickPayRule platform = ClaimQuickPayRule.create("RULE-P", "platform", "MEDICAL", true,
                new BigDecimal("5000"));
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID))
                .thenReturn(Optional.of(view("PROCESSING", new BigDecimal("3000"), null)));
        when(quickPayRuleRepository.findByBusinessKey(TENANT_ID, "MEDICAL")).thenReturn(Optional.empty());
        when(quickPayRuleRepository.findPlatformDefault("MEDICAL")).thenReturn(Optional.of(platform));

        orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID);

        verify(commandGateway, Mockito.times(3)).sendAndWait(any());
    }

    @Test
    @DisplayName("案件不存在 → CLAIM_NOT_EXIST")
    void shouldThrowClaimNotExist() {
        when(claimViewRepository.findByClaimIdAndTenantId(CLAIM_ID, TENANT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orchestrator.executeQuickPay(CLAIM_ID, TENANT_ID));
        assertEquals(ClaimErrorCode.CLAIM_NOT_EXIST.getCode(), ex.getErrorCode());
        verify(commandGateway, never()).sendAndWait(any());
    }
}
