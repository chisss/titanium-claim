package com.titanium.claim.common.exception;

import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单未生效异常
 * <p>
 * 当理赔关联的保单状态非 ACTIVE（生效）时抛出。
 * </p>
 */
public class PolicyNotActiveException extends DomainException {
    public PolicyNotActiveException() {
        super(ClaimErrorCode.POLICY_NOT_ACTIVE);
    }
}
