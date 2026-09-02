package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.common.enums.RejectReason;
import com.titanium.claim.valueobject.ClaimId;

/**
 * 拒赔命令
 * <p>
 * 核赔否决：仅 PENDING/PROCESSING 状态可拒赔，拒赔原因必须携带枚举（禁止裸字符串），
 * 流转至 REJECTED 终态并发布 {@code ClaimRejectedEvent}（触发拒赔通知书发送）。
 * </p>
 *
 * @param claimId 理赔案件ID
 * @param reason 拒赔原因枚举
 * @param comment 拒赔意见说明
 */
public record RejectClaimCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        RejectReason reason,
        String comment
) {
}
