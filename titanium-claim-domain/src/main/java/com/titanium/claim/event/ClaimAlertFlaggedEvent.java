package com.titanium.claim.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.claim.valueobject.AlertFlag;
import com.titanium.claim.valueobject.ClaimId;

/**
 * 理赔警示标记事件（反欺诈警示 + 统计口径标记）
 * <p>
 * {@link FlagClaimAlertCommand} 处理通过后发布，携带合并后的全部警示标记，
 * 由读模型投影刷新 {@code t_claim_view.alert_flags} 列（快赔通道判据的数据来源）。
 * </p>
 *
 * @param claimId   理赔案件ID
 * @param flags     合并后的全部警示标记（类型枚举 + 命中规则标识）
 * @param flaggedAt 打标时间
 */
public record ClaimAlertFlaggedEvent(
        ClaimId claimId,
        List<AlertFlag> flags,
        LocalDateTime flaggedAt
) {
}
