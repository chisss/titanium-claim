package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.common.enums.RejectReason;
import com.titanium.claim.valueobject.ClaimId;

/**
 * 理赔拒赔事件
 * <p>
 * 核赔否决后发布：携枚举化拒赔原因与客户/保单标识，投影流转读模型至 REJECTED，
 * 并经 Kafka 触发拒赔通知书发送（3 日时限）。
 * </p>
 */
public record ClaimRejectedEvent(
        ClaimId claimId,
        String policyId,
        String customerId,
        RejectReason reason,
        String comment,
        LocalDateTime rejectedAt
) {
}
