package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 保单不存在异常
 * <p>
 * 当理赔关联的保单查询不到时抛出。
 * </p>
 */
public class PolicyNotFoundException extends BusinessException {
    public PolicyNotFoundException() {
        super(ClaimErrorCode.POLICY_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
}
