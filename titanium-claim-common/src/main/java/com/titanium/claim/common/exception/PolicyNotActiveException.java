package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 保单未生效异常
 * <p>
 * 当理赔关联的保单状态非 ACTIVE（生效）时抛出。
 * </p>
 */
public class PolicyNotActiveException extends BusinessException {
    public PolicyNotActiveException() {
        super(ClaimErrorCode.POLICY_NOT_ACTIVE, HttpStatus.BAD_REQUEST);
    }
}
