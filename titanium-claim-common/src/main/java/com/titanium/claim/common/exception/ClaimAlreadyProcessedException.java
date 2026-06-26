package com.titanium.claim.common.exception;

import com.titanium.claim.common.constant.ClaimConstants;
import org.springframework.http.HttpStatus;

public class ClaimAlreadyProcessedException extends BusinessException {
    public ClaimAlreadyProcessedException() {
        super(ClaimConstants.CLAIM_ALREADY_PROCESSED, "CLAIM_ALREADY_PROCESSED", HttpStatus.BAD_REQUEST);
    }
}