package com.titanium.claim.infrastructure.entity;

import java.math.BigDecimal;

import com.titanium.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
    /** 主键（雪花，独立于业务ID列） */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;


    /** 规则ID（雪花，主键） */
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
    /**
     * 乐观锁版本（JPA 自动维护）。
     * <p>
     * m20-09（审查报告 PER-01）补齐：原先写侧 DO 无 {@code @Version}，并发更新走无条件 UPDATE，
     * 后写者静默覆盖前写者、数据丢失。加此字段后 JPA 的 UPDATE 会带 {@code WHERE version = ?}，
     * 冲突时抛 {@code ObjectOptimisticLockingFailureException}（由异常处理器映射为 409）。
     * </p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
