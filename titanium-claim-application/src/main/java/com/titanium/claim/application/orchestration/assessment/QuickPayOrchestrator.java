package com.titanium.claim.application.orchestration.assessment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

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
import com.titanium.claim.valueobject.AlertFlag;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 快赔自动核赔编排器（application/orchestration/assessment，产品文档 §2.10）
 * <p>
 * 小额快赔通道的同步命令式编排：<b>读模型取案件（金额/状态/警示）→ 快赔规则匹配（租户覆盖 &gt; 平台默认）→
 * 判据（规则开启 + 金额 ≤ 阈值 + 状态 PROCESSING + 无欺诈警示标记）→ 自动核赔</b>
 * （变更状态 APPROVED → 核赔结算 → 打快赔统计标记 QUICK_PAY）。
 * 判据不满足以领域异常中断（不产生任何命令）；快赔标记是统计口径标记，不阻断后续快赔（避免自我阻断）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuickPayOrchestrator {

    private final ClaimViewRepository         claimViewRepository;
    private final ClaimQuickPayRuleRepository quickPayRuleRepository;
    private final CommandGateway              commandGateway;

    /**
     * 快赔自动核赔：读模型取数 → 规则匹配 → 判据 → 自动核赔三命令。
     *
     * @param claimId  理赔案件ID
     * @param tenantId 租户ID
     */
    public void executeQuickPay(String claimId, String tenantId) {
        ClaimView view = claimViewRepository.findByClaimIdAndTenantId(claimId, tenantId)
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_NOT_EXIST,
                        "理赔案件不存在: " + claimId));
        ClaimQuickPayRule rule = resolveRule(tenantId, view);
        if (!rule.matches(view.getClaimAmount())) {
            throw new BusinessException(ClaimErrorCode.CLAIM_STATUS_PRECONDITION_NOT_MET,
                    "快赔判据不满足: 规则未开启或金额超阈值");
        }
        if (view.getStatus() != ClaimStatus.PROCESSING) {
            throw new BusinessException(ClaimErrorCode.CLAIM_STATUS_PRECONDITION_NOT_MET,
                    "快赔判据不满足: 仅 PROCESSING 状态可自动核赔, 当前=" + view.getStatus());
        }
        if (hasFraudAlert(view.getAlertFlags())) {
            throw new BusinessException(ClaimErrorCode.CLAIM_STATUS_PRECONDITION_NOT_MET,
                    "快赔判据不满足: 存在欺诈警示标记");
        }
        // 判据全过：变更状态 APPROVED → 核赔结算 → 打快赔统计标记
        commandGateway.sendAndWait(new ChangeClaimStatusCommand(ClaimId.of(claimId), ClaimStatus.APPROVED,
                ClaimConstants.QUICK_PAY_CONCLUSION));
        BigDecimal providedAmount = resolveProvidedAmount(view);
        commandGateway.sendAndWait(new SettleClaimCommand(ClaimId.of(claimId), providedAmount,
                ClaimEnum.PayoutMethod.BANK_TRANSFER, null, ClaimConstants.QUICK_PAY_CONCLUSION));
        commandGateway.sendAndWait(new FlagClaimAlertCommand(ClaimId.of(claimId),
                List.of(new AlertFlag(AlertType.QUICK_PAY, ClaimConstants.AlertRule.RULE_QUICK_PAY))));
        log.info("[快赔编排] 自动核赔完成, claimId={}, assessed={}, provided={}", claimId,
                view.getAssessedPayableAmount(), providedAmount);
    }

    /**
     * 结算金额来源：定损在案的案件<b>不传值</b>（交聚合以定损核定额裁决），未定损案件传读模型申报金额。
     * <p>
     * 🔴 <b>为何定损在案不传值（m16-1905）</b>：聚合 {@code Claim.resolveSettledAmount} 对已定损案件
     * <b>只认</b>定损核定额（{@code =（定损总金额−残值）×责任比例}）——调用方传值不等即拒。原快赔实现
     * 无条件透传读模型 {@code claimAmount}（案件<b>申报</b>金额），对已定损案件必然与核定额不等 ⇒
     * 结算被拒、快赔在已定损场景下断链（而这恰是快赔最常见的自动场景）。
     * 改判据为「定损在案即不指定金额」后，金额口径与聚合判定同源：核定额由聚合自己算、自己用，
     * 快赔不再产生第二套金额来源。读模型滞后（定损事件尚未投影）时退化为传申报金额、由聚合拒绝——
     * 失败关闭，不会错额支付。
     * </p>
     */
    private BigDecimal resolveProvidedAmount(ClaimView view) {
        return view.getAssessedPayableAmount() == null ? view.getClaimAmount() : null;
    }

    /**
     * 规则匹配：租户覆盖 &gt; 平台默认（platform），均未命中抛配置缺失异常。
     */
    private ClaimQuickPayRule resolveRule(String tenantId, ClaimView view) {
        String claimType = view.getClaimType() == null ? null : view.getClaimType().getCode();
        return quickPayRuleRepository.findByBusinessKey(tenantId, claimType)
                .or(() -> quickPayRuleRepository.findPlatformDefault(claimType))
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "快赔规则未配置: 理赔类型=" + claimType));
    }

    /**
     * 欺诈警示判定：读模型 alert_flags 逗号分隔 code 还原枚举后复用 {@link AlertFlag#isFraudAlert()}
     * （延迟报案/多次报案/风险评分阻断快赔；快赔标记自身是统计口径标记不阻断）。
     */
    private boolean hasFraudAlert(String alertFlags) {
        if (alertFlags == null || alertFlags.isBlank()) {
            return false;
        }
        return Stream.of(alertFlags.split(","))
                .map(AlertType::fromCode)
                .filter(Objects::nonNull)
                .anyMatch(type -> new AlertFlag(type, null).isFraudAlert());
    }
}
