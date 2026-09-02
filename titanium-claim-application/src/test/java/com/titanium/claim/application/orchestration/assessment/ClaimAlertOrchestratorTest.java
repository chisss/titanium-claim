package com.titanium.claim.application.orchestration.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.titanium.claim.command.FlagClaimAlertCommand;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.enums.AlertType;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.view.ClaimView;

/**
 * 理赔警示自动打标编排器测试（dev-012b：报案环节反欺诈自动风险评分）
 * <p>
 * 验证 {@link ClaimAlertOrchestrator} 两条 P1 判据：延迟报案（报案时间距出险时间超 30 天）与
 * 多次报案（同保单 30 天窗口内存在其他报案），命中即发 {@link FlagClaimAlertCommand}（聚合根幂等合并），
 * 全部未命中静默跳过。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimAlertOrchestratorTest {

    @Mock
    private CommandGateway      commandGateway;
    @Mock
    private ClaimViewRepository claimViewRepository;

    private ClaimAlertOrchestrator orchestrator;

    private static final String CLAIM_ID  = "CLAIM-1";
    private static final String POLICY_ID = "POL-1";
    private static final String TENANT_ID = "T-1";

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimAlertOrchestrator(commandGateway, claimViewRepository);
    }

    private ClaimView otherClaim() {
        ClaimView view = new ClaimView();
        view.setClaimId("CLAIM-OTHER");
        view.setCreateTime(LocalDateTime.now().minusDays(5));
        return view;
    }

    @Test
    @DisplayName("延迟报案：报案时间距出险超 30 天 → 打 LATE_REPORT")
    void shouldFlagLateReport() {
        LocalDateTime reportAt = LocalDateTime.now();
        LocalDateTime incidentDate = reportAt.minusDays(31);

        orchestrator.scoreAndFlag(CLAIM_ID, POLICY_ID, incidentDate, reportAt, TENANT_ID);

        ArgumentCaptor<FlagClaimAlertCommand> captor = ArgumentCaptor.forClass(FlagClaimAlertCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertEquals(1, captor.getValue().flags().size());
        assertEquals(AlertType.LATE_REPORT, captor.getValue().flags().get(0).type());
        assertEquals(ClaimConstants.AlertRule.RULE_LATE_REPORT, captor.getValue().flags().get(0).ruleCode());
    }

    @Test
    @DisplayName("未延迟报案：30 天内报案不打 LATE_REPORT")
    void shouldNotFlagWhenReportedWithinWindow() {
        LocalDateTime reportAt = LocalDateTime.now();
        when(claimViewRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(List.of());

        orchestrator.scoreAndFlag(CLAIM_ID, POLICY_ID, reportAt.minusDays(5), reportAt, TENANT_ID);

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("多次报案：同保单 30 天窗口内存在其他报案 → 打 MULTIPLE_REPORTS")
    void shouldFlagMultipleReports() {
        LocalDateTime reportAt = LocalDateTime.now();
        when(claimViewRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(List.of(otherClaim()));

        orchestrator.scoreAndFlag(CLAIM_ID, POLICY_ID, reportAt.minusDays(1), reportAt, TENANT_ID);

        ArgumentCaptor<FlagClaimAlertCommand> captor = ArgumentCaptor.forClass(FlagClaimAlertCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertEquals(1, captor.getValue().flags().size());
        assertEquals(AlertType.MULTIPLE_REPORTS, captor.getValue().flags().get(0).type());
        assertEquals(ClaimConstants.AlertRule.RULE_MULTIPLE_REPORTS, captor.getValue().flags().get(0).ruleCode());
    }

    @Test
    @DisplayName("窗口外其他报案：不构成多次报案")
    void shouldNotFlagWhenOtherClaimOutsideWindow() {
        ClaimView stale = new ClaimView();
        stale.setClaimId("CLAIM-OTHER");
        stale.setCreateTime(LocalDateTime.now().minusDays(31));
        when(claimViewRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(List.of(stale));

        orchestrator.scoreAndFlag(CLAIM_ID, POLICY_ID, LocalDateTime.now().minusDays(1), LocalDateTime.now(), TENANT_ID);

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("双判据命中：延迟报案 + 多次报案一并打标")
    void shouldFlagBothWhenBothCriteriaHit() {
        LocalDateTime reportAt = LocalDateTime.now();
        when(claimViewRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID))
                .thenReturn(List.of(otherClaim()));

        orchestrator.scoreAndFlag(CLAIM_ID, POLICY_ID, reportAt.minusDays(40), reportAt, TENANT_ID);

        ArgumentCaptor<FlagClaimAlertCommand> captor = ArgumentCaptor.forClass(FlagClaimAlertCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertEquals(2, captor.getValue().flags().size());
    }

    @Test
    @DisplayName("本单报案在窗口内不误判为多次报案（排除自身）")
    void shouldExcludeSelfFromMultipleReportDetection() {
        ClaimView self = new ClaimView();
        self.setClaimId(CLAIM_ID);
        self.setCreateTime(LocalDateTime.now());
        when(claimViewRepository.findByPolicyIdAndTenantId(POLICY_ID, TENANT_ID)).thenReturn(List.of(self));

        orchestrator.scoreAndFlag(CLAIM_ID, POLICY_ID, LocalDateTime.now().minusDays(1), LocalDateTime.now(), TENANT_ID);

        verify(commandGateway, never()).sendAndWait(any());
    }
}
