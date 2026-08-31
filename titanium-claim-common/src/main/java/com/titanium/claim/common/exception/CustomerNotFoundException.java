package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 客户不存在异常
 * <p>
 * 当理赔关联的客户主数据不存在时抛出。
 * </p>
 */
public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException() {
        super(ClaimErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
}
