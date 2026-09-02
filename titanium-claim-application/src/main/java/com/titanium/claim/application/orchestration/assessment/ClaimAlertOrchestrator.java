package com.titanium.claim.application.orchestration.assessment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.command.FlagClaimAlertCommand;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.enums.AlertType;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.valueobject.AlertFlag;
import com.titanium.claim.valueobject.ClaimId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔警示自动打标编排器（application/orchestration/assessment）
 * <p>
 * 报案环节反欺诈警示的自动风险评分（P1 启发式评分）：<b>延迟报案</b>（报案时间距出险时间超 30 天）与
 * <b>多次报案</b>（同保单 30 天窗口内存在其他报案）两条判据，命中后发 {@link FlagClaimAlertCommand}
 * 打标（聚合根按类型合并去重，幂等），投影至读模型 {@code alert_flags} 列，供快赔通道判据
 * 「无欺诈警示标记」与人工复核队列使用。
 * </p>
 * <p>
 * 多次报案判据基于读模型（投影最终一致），启发式评分可接受——风险评分是筛查手段而非强一致决策
 * （§3.4.9 ⑥ 的「领域查询禁复用读模型」仅约束领域内部强一致决策，不约束应用层风控启发式）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimAlertOrchestrator {

    private final CommandGateway      commandGateway;
    private final ClaimViewRepository claimViewRepository;

    /**
     * 报案后自动风险评分：延迟报案/多次报案判据命中即打标，全部未命中则静默跳过。
     *
     * @param claimId      新报案案件 ID
     * @param policyId     保单 ID
     * @param incidentDate 出险时间
     * @param reportAt     报案时间
     * @param tenantId     租户 ID
     */
    public void scoreAndFlag(String claimId, String policyId, LocalDateTime incidentDate,
            LocalDateTime reportAt, String tenantId) {
        List<AlertFlag> flags = new ArrayList<>();
        // 判据一：延迟报案——报案时间距出险时间超过 30 天
        if (reportAt != null && incidentDate != null
                && reportAt.isAfter(incidentDate.plusDays(ClaimConstants.AlertRule.LATE_REPORT_WINDOW_DAYS))) {
            flags.add(new AlertFlag(AlertType.LATE_REPORT, ClaimConstants.AlertRule.RULE_LATE_REPORT));
        }
        // 判据二：多次报案——同保单 30 天窗口内存在其他报案
        if (hasRecentOtherClaims(policyId, claimId, reportAt, tenantId)) {
            flags.add(new AlertFlag(AlertType.MULTIPLE_REPORTS, ClaimConstants.AlertRule.RULE_MULTIPLE_REPORTS));
        }
        if (flags.isEmpty()) {
            log.info("[警示打标编排] 未命中任何警示判据, claimId={}", claimId);
            return;
        }
        commandGateway.sendAndWait(new FlagClaimAlertCommand(ClaimId.of(claimId), flags));
        log.info("[警示打标编排] 警示标记已打标, claimId={}, flagCodes={}", claimId,
                flags.stream().map(flag -> flag.type().getCode()).toList());
    }

    /**
     * 多次报案判据：同保单在 30 天窗口内存在其他报案（排除本单，读模型按创建时间近窗口过滤）。
     */
    private boolean hasRecentOtherClaims(String policyId, String claimId, LocalDateTime reportAt, String tenantId) {
        LocalDateTime windowStart = (reportAt == null ? LocalDateTime.now() : reportAt)
                .minusDays(ClaimConstants.AlertRule.MULTIPLE_REPORTS_WINDOW_DAYS);
        return claimViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .anyMatch(view -> !view.getClaimId().equals(claimId)
                        && view.getCreateTime() != null && view.getCreateTime().isAfter(windowStart));
    }
}
