package com.titanium.claim.common.exception;

import com.titanium.claim.common.constant.ClaimConstants;
import org.springframework.http.HttpStatus;

public class ClaimOutOfCoverageException extends BusinessException {
    public ClaimOutOfCoverageException() {
        super(ClaimConstants.CLAIM_OUT_OF_COVERAGE, "CLAIM_OUT_OF_COVERAGE", HttpStatus.BAD_REQUEST);
    }
}