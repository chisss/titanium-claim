package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.claim.common.constant.ClaimConstants;

public class PolicyNotActiveException extends BusinessException {
    public PolicyNotActiveException() {
        super(ClaimConstants.POLICY_NOT_ACTIVE, "POLICY_NOT_ACTIVE", HttpStatus.BAD_REQUEST);
    }
}
