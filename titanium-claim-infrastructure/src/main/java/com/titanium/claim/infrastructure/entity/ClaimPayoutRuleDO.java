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
 * 赔付规则持久化对象（理赔配置支撑子域，状态存储）
 * <p>
 * 映射表 {@code t_claim_payout_rule}。聚合根 {@code ClaimPayoutRule} 与 DO 的转换经
 * {@code ClaimConfigPersistenceMapper}（MapStruct），医院分档比例/责任免除落库 JSON 文本。
 * </p>
 */
@Entity
@Table(name = "t_claim_payout_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_claim_payout_rule_biz",
                columnNames = {"tenant_id", "insurance_line", "claim_type"}),
        indexes = @Index(name = "idx_claim_payout_rule_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClaimPayoutRuleDO extends BaseEntity {

    /** 规则ID（雪花，主键） */
    @Id
    @Column(name = "rule_id", nullable = false, length = 32)
    private String ruleId;

    /** 险种线 code（metadata InsuranceProductType） */
    @Column(name = "insurance_line", nullable = false, length = 32)
    private String insuranceLine;

    /** 理赔类型 code（metadata ClaimEnum.ClaimType） */
    @Column(name = "claim_type", nullable = false, length = 50)
    private String claimType;

    /** 免赔额（元） */
    @Column(name = "deductible", precision = 18, scale = 2)
    private BigDecimal deductible;

    /** 赔付比例（0-100 百分比） */
    @Column(name = "payout_ratio")
    private Integer payoutRatio;

    /** 单次限额（元，空=不限） */
    @Column(name = "per_claim_limit", precision = 18, scale = 2)
    private BigDecimal perClaimLimit;

    /** 年度限额（元，空=不限） */
    @Column(name = "annual_limit", precision = 18, scale = 2)
    private BigDecimal annualLimit;

    /** 医院分档赔付比例（JSON 对象文本：档位→0-100） */
    @Column(name = "hospital_tier_ratios", columnDefinition = "TEXT")
    private String hospitalTierRatios;

    /** 责任免除清单（JSON 数组文本） */
    @Column(name = "exclusions", columnDefinition = "TEXT")
    private String exclusions;
}
