package com.titanium.claim.common.exception;

import com.titanium.claim.common.constant.ClaimConstants;
import org.springframework.http.HttpStatus;

public class InvalidClaimAmountException extends BusinessException {
    public InvalidClaimAmountException() {
        super(ClaimConstants.INVALID_CLAIM_AMOUNT, "INVALID_CLAIM_AMOUNT", HttpStatus.BAD_REQUEST);
    }
}