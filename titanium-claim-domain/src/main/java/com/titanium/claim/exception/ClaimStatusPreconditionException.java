package com.titanium.claim.exception;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 理赔状态不满足操作前置条件异常
 * <p>
 * 当理赔案件的当前状态不满足某操作的前置条件时抛出，如：
 * <ul>
 *   <li>核赔结算要求状态为 APPROVED</li>
 *   <li>案件关闭要求状态为 PAID 或 REJECTED</li>
 * </ul>
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class ClaimStatusPreconditionException extends DomainException {

    public ClaimStatusPreconditionException(ClaimId claimId, ClaimStatus currentStatus,
                                            String operation, String requiredStatus) {
        super(ClaimErrorCode.CLAIM_STATUS_PRECONDITION_NOT_MET,
              String.format("理赔案件[%s] 操作 %s 要求状态为 %s，当前状态: %s",
                            claimId.value(), operation, requiredStatus, currentStatus.name()));
    }
}
