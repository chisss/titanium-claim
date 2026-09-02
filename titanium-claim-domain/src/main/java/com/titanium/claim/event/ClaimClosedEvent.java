package com.titanium.claim.event;

import java.time.LocalDateTime;

import com.titanium.claim.valueobject.ClaimId;

/**
 * 理赔结案事件
 * <p>
 * PAID/REJECTED 终态案件归档结案，流转读模型至 CLOSED。
 * </p>
 */
public record ClaimClosedEvent(
        ClaimId claimId,
        LocalDateTime closedAt
) {
}
