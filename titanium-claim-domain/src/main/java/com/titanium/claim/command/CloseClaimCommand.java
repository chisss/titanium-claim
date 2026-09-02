package com.titanium.claim.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.ClaimId;

/**
 * 结案命令
 * <p>
 * 仅 PAID/REJECTED 终态可结案，流转至 CLOSED 终态并发布 {@code ClaimClosedEvent}（归档）。
 * </p>
 *
 * @param claimId 理赔案件ID
 */
public record CloseClaimCommand(
        @TargetAggregateIdentifier ClaimId claimId
) {
}
