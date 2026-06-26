package com.titanium.claim.common.exception;

import com.titanium.claim.common.constant.ClaimConstants;
import org.springframework.http.HttpStatus;

public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException() {
        super(ClaimConstants.CUSTOMER_NOT_FOUND, "CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}