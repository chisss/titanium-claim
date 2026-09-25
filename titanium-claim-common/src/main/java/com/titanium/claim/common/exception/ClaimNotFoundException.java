package com.titanium.claim.common.exception;

import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 理赔不存在异常
 * <p>
 * 当按理赔案件ID/理赔编号查询不到理赔记录时抛出。
 * </p>
 */
public class ClaimNotFoundException extends DomainException {
    public ClaimNotFoundException() {
        super(ClaimErrorCode.CLAIM_NOT_EXIST);
    }
}
