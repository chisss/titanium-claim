package com.titanium.claim.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;

import lombok.Data;

/**
 * 理赔案件查询结果（CQRS 读侧稳定返回契约）
 */
@Data
public class ClaimQueryResult {
    private String              claimId;
    private String              customerId;
    private String              policyId;
    private String              claimNumber;
    private ClaimEnum.ClaimType claimType;
    private LocalDateTime       incidentDate;
    private String              incidentDescription;
    private BigDecimal          claimAmount;
    private ClaimStatus         status;
    private ClaimPhase          phase;
    private BigDecimal          settledAmount;
    private LocalDateTime       createdAt;
    private LocalDateTime       updatedAt;
}
