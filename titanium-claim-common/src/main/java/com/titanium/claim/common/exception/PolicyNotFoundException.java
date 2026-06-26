package com.titanium.claim.common.exception;

import com.titanium.claim.common.constant.ClaimConstants;
import org.springframework.http.HttpStatus;

public class PolicyNotFoundException extends BusinessException {
    public PolicyNotFoundException() {
        super(ClaimConstants.POLICY_NOT_FOUND, "POLICY_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}