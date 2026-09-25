package com.titanium.claim.common.exception;

import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 客户不存在异常
 * <p>
 * 当理赔关联的客户主数据不存在时抛出。
 * </p>
 */
public class CustomerNotFoundException extends DomainException {
    public CustomerNotFoundException() {
        super(ClaimErrorCode.CUSTOMER_NOT_FOUND);
    }
}
