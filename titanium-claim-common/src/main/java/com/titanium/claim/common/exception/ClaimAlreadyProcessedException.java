package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 理赔已处理异常
 * <p>
 * 当对已处理（已结案/已赔付）的理赔案件重复操作时抛出。
 * </p>
 */
public class ClaimAlreadyProcessedException extends BusinessException {
    public ClaimAlreadyProcessedException() {
        super(ClaimErrorCode.CLAIM_ALREADY_PROCESSED, HttpStatus.BAD_REQUEST);
    }
}
