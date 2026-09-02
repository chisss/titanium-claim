package com.titanium.claim.infrastructure.entity;

import java.math.BigDecimal;

import com.titanium.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 快赔规则持久化对象（理赔配置支撑子域，状态存储）
 * <p>
 * 映射表 {@code t_claim_quick_pay_rule}。聚合根 {@code ClaimQuickPayRule} 与 DO 的转换经
 * {@code ClaimConfigPersistenceMapper}（MapStruct）。
 * </p>
 */
@Entity
@Table(name = "t_claim_quick_pay_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_claim_quick_pay_rule_biz",
                columnNames = {"tenant_id", "claim_type"}),
        indexes = @Index(name = "idx_claim_quick_pay_rule_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClaimQuickPayRuleDO extends BaseEntity {

    /** 规则ID（雪花，主键） */
    @Id
    @Column(name = "rule_id", nullable = false, length = 32)
    private String ruleId;

    /** 适用理赔类型 code（metadata ClaimEnum.ClaimType） */
    @Column(name = "claim_type", nullable = false, length = 50)
    private String claimType;

    /** 通道开关（1=开启，0=关闭） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 快赔金额阈值（元） */
    @Column(name = "amount_threshold", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountThreshold;
}
