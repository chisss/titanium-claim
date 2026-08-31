package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 理赔不存在异常
 * <p>
 * 当按理赔案件ID/理赔编号查询不到理赔记录时抛出。
 * </p>
 */
public class ClaimNotFoundException extends BusinessException {
    public ClaimNotFoundException() {
        super(ClaimErrorCode.CLAIM_NOT_EXIST, HttpStatus.NOT_FOUND);
    }
}
