package com.titanium.claim.infrastructure.repository.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.claim.enums.ClaimStatus;
import com.titanium.common.jpa.BaseEntity;
import com.titanium.metadata.enums.claim.ClaimEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 理赔案件数据库实体
 * <p>
 * 继承 {@link BaseEntity}，复用租户ID、创建/更新时间、创建/更新人、逻辑删除等公共审计字段。 原 created_at/updated_at
 * 列统一为基类的 create_time/update_time。
 * </p>
 */
@Entity
@Table(name = "t_claim")
@Getter
@Setter
@NoArgsConstructor
public class ClaimEntity extends BaseEntity {
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
}
