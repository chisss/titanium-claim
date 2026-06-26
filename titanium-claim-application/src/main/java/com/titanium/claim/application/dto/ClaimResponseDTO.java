package com.titanium.claim.application.dto;

import com.titanium.claim.enums.ClaimStatus;
import com.titanium.metadata.enums.claim.ClaimEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ClaimResponseDTO {
    private String claimId;
    private String customerId;
    private String policyId;
    private String claimNumber;
    private String claimType;
    private LocalDateTime incidentDate;
    private String incidentDescription;
    private BigDecimal claimAmount;
    private String status;
    private String statusDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void setStatus(ClaimStatus status) {
        if (status != null) {
            this.status = status.getCode();
            this.statusDescription = status.getDescription();
        }
    }

    public void setClaimType(ClaimEnum.ClaimType claimType) {
        if (claimType != null) {
            this.claimType = claimType.getCode();
        }
    }
}