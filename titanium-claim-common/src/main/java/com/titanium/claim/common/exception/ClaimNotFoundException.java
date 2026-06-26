package com.titanium.claim.common.exception;

import com.titanium.claim.common.constant.ClaimConstants;
import org.springframework.http.HttpStatus;

public class ClaimNotFoundException extends BusinessException {
    public ClaimNotFoundException() {
        super(ClaimConstants.CLAIM_NOT_FOUND, "CLAIM_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}