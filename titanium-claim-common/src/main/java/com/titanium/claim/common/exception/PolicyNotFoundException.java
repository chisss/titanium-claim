package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.claim.common.constant.ClaimConstants;

public class PolicyNotFoundException extends BusinessException {
    public PolicyNotFoundException() {
        super(ClaimConstants.POLICY_NOT_FOUND, "POLICY_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
