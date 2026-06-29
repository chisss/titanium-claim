package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.claim.common.constant.ClaimConstants;

public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException() {
        super(ClaimConstants.CUSTOMER_NOT_FOUND, "CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
