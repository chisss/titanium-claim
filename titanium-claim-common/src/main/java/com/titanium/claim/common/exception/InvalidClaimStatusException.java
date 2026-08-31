package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 无效理赔状态异常
 * <p>
 * 当传入的理赔状态码无法解析为 {@code ClaimStatus} 枚举时抛出。
 * </p>
 */
public class InvalidClaimStatusException extends BusinessException {
    public InvalidClaimStatusException() {
        super(ClaimErrorCode.CLAIM_STATUS_ERROR, HttpStatus.BAD_REQUEST);
    }
}
