package com.titanium.claim.common.exception;

import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单不存在异常
 * <p>
 * 当理赔关联的保单查询不到时抛出。
 * </p>
 */
public class PolicyNotFoundException extends DomainException {
    public PolicyNotFoundException() {
        super(ClaimErrorCode.POLICY_NOT_FOUND);
    }
}
