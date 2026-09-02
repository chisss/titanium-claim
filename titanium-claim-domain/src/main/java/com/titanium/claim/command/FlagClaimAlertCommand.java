package com.titanium.claim.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.AlertFlag;
import com.titanium.claim.valueobject.ClaimId;

/**
 * 理赔警示标记命令（反欺诈警示 + 统计口径标记）
 * <p>
 * 报案环节自动风险评分（延迟报案/多次报案）、人工复核标记与快赔通道标记的统一入口。
 * 聚合根按类型合并（已存在的类型不重复打标），发布 {@code ClaimAlertFlaggedEvent}
 * 投影至读模型（快赔通道判据「无欺诈警示标记」的数据来源）。
 * </p>
 *
 * @param claimId 理赔案件ID
 * @param flags   警示标记列表（类型枚举 + 命中规则标识，禁止裸字符串描述）
 */
public record FlagClaimAlertCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        List<AlertFlag> flags
) {
}
