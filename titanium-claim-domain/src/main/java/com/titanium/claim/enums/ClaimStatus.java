package com.titanium.claim.enums;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.exception.InvalidClaimStatusException;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ClaimStatus {
    PENDING(ClaimConstants.CLAIM_STATUS_PENDING, "待处理"),
    PROCESSING(ClaimConstants.CLAIM_STATUS_PROCESSING, "处理中"),
    APPROVED(ClaimConstants.CLAIM_STATUS_APPROVED, "已批准"),
    REJECTED(ClaimConstants.CLAIM_STATUS_REJECTED, "已拒绝"),
    PAID(ClaimConstants.CLAIM_STATUS_PAID, "已支付");

    private final String code;
    private final String description;

    ClaimStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ClaimStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(InvalidClaimStatusException::new);
    }

    public static ClaimStatus fromDescription(String description) {
        return Arrays.stream(values())
                .filter(status -> status.description.equals(description))
                .findFirst()
                .orElseThrow(InvalidClaimStatusException::new);
    }

    @Override
    public String toString() {
        return code;
    }
}