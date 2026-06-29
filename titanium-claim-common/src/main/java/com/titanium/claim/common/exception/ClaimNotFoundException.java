package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.claim.common.constant.ClaimConstants;

public class ClaimNotFoundException extends BusinessException {
    public ClaimNotFoundException() {
        super(ClaimConstants.CLAIM_NOT_FOUND, "CLAIM_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
