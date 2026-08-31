package com.titanium.claim.exception;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.IllegalStateTransitionException;

/**
 * 理赔状态流转非法异常
 * <p>
 * 当理赔案件状态流转违反状态机规则时抛出，如：
 * <ul>
 *   <li>PENDING 只能流转到 PROCESSING 或 REJECTED</li>
 *   <li>PROCESSING 只能流转到 APPROVED 或 REJECTED</li>
 *   <li>APPROVED 只能流转到 PAID</li>
 *   <li>PAID 和 REJECTED 为终态，不可再流转</li>
 * </ul>
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class ClaimStatusTransitionException extends IllegalStateTransitionException {

    public ClaimStatusTransitionException(ClaimId claimId, ClaimStatus from, ClaimStatus to) {
        super(ClaimErrorCode.CLAIM_STATUS_TRANSITION_INVALID, "理赔案件", claimId.value(), from.name(), to.name());
    }
}
