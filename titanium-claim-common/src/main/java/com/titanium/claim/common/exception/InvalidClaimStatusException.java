package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.claim.common.constant.ClaimConstants;

public class InvalidClaimStatusException extends BusinessException {
    public InvalidClaimStatusException() {
        super(ClaimConstants.INVALID_CLAIM_STATUS, "INVALID_CLAIM_STATUS", HttpStatus.BAD_REQUEST);
    }
}
