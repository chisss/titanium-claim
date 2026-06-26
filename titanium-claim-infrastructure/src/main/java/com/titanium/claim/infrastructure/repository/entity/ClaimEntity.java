package com.titanium.claim.infrastructure.repository.entity;

import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.metadata.enums.claim.ClaimEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_claim")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClaimEntity {
    @Id
    @Column(name = "claim_id", nullable = false, length = 36)
    private String claimId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "policy_id", nullable = false, length = 36)
    private String policyId;

    @Column(name = "claim_number", nullable = false, length = 50, unique = true)
    private String claimNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 50)
    private ClaimEnum.ClaimType claimType;

    @Column(name = "incident_date", nullable = false)
    private LocalDateTime incidentDate;

    @Column(name = "incident_description", nullable = false, columnDefinition = "TEXT")
    private String incidentDescription;

    @Column(name = "claim_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal claimAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    // 转换为领域对象
    public com.titanium.claim.aggregate.Claim toDomain() {
        com.titanium.claim.aggregate.Claim claim = new com.titanium.claim.aggregate.Claim();
        claim.setClaimId(ClaimId.of(claimId));
        claim.setCustomerId(CustomerId.of(customerId));
        claim.setPolicyId(PolicyId.of(policyId));
        claim.setClaimNumber(claimNumber);
        claim.setClaimType(claimType);
        claim.setIncidentDate(incidentDate);
        claim.setIncidentDescription(incidentDescription);
        claim.setClaimAmount(ClaimAmount.of(claimAmount));
        claim.setStatus(status);
        claim.setCreatedAt(createdAt);
        claim.setUpdatedAt(updatedAt);
        return claim;
    }

    // 从领域对象转换
    public static ClaimEntity fromDomain(com.titanium.claim.aggregate.Claim claim, String tenantId) {
        return new ClaimEntity(
                claim.getClaimId().value(),
                claim.getCustomerId().value(),
                claim.getPolicyId().value(),
                claim.getClaimNumber(),
                claim.getClaimType(),
                claim.getIncidentDate(),
                claim.getIncidentDescription(),
                claim.getClaimAmount().value(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                tenantId
        );
    }
}