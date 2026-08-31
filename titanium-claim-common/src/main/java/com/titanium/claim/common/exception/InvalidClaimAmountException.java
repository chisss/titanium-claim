package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 无效理赔金额异常
 * <p>
 * 当理赔金额为空、非正数或超出合理范围时抛出。
 * </p>
 */
public class InvalidClaimAmountException extends BusinessException {

    public InvalidClaimAmountException() {
        super(ClaimErrorCode.CLAIM_AMOUNT_ERROR, HttpStatus.BAD_REQUEST);
    }

    /**
     * 携带业务上下文消息构造（如"核定赔付金额必须大于0"）
     *
     * @param message 具体校验失败消息
     */
    public InvalidClaimAmountException(String message) {
        super(ClaimErrorCode.CLAIM_AMOUNT_ERROR, message, HttpStatus.BAD_REQUEST);
    }
}
