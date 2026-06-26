package com.titanium.claim.common.exception;

import com.titanium.claim.common.constant.ClaimConstants;
import org.springframework.http.HttpStatus;

public class PolicyNotActiveException extends BusinessException {
    public PolicyNotActiveException() {
        super(ClaimConstants.POLICY_NOT_ACTIVE, "POLICY_NOT_ACTIVE", HttpStatus.BAD_REQUEST);
    }
}