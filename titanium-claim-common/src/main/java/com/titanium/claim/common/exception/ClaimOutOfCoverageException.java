package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 理赔不在保险范围内异常
 * <p>
 * 当出险事故不属于保单保险责任范围时抛出。
 * </p>
 */
public class ClaimOutOfCoverageException extends BusinessException {
    public ClaimOutOfCoverageException() {
        super(ClaimErrorCode.CLAIM_OUT_OF_COVERAGE, HttpStatus.BAD_REQUEST);
    }
}
