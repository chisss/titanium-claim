package com.titanium.claim.infrastructure.entity;

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
 * 时限规则持久化对象（理赔配置支撑子域，状态存储）
 * <p>
 * 映射表 {@code t_claim_time_limit_rule}。聚合根 {@code ClaimTimeLimitRule} 与 DO 的转换经
 * {@code ClaimConfigPersistenceMapper}（MapStruct）。
 * </p>
 */
@Entity
@Table(name = "t_claim_time_limit_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_claim_time_limit_rule_biz",
                columnNames = {"tenant_id", "insurance_line", "claim_stage"}),
        indexes = @Index(name = "idx_claim_time_limit_rule_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClaimTimeLimitRuleDO extends BaseEntity {
    /** 主键（雪花，独立于业务ID列） */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;


    /** 规则ID（雪花，主键） */
        @Column(name = "rule_id", nullable = false, length = 32)
    private String ruleId;

    /** 险种线 code（metadata InsuranceProductType） */
    @Column(name = "insurance_line", nullable = false, length = 32)
    private String insuranceLine;

    /** 案件环节 code（对齐流程模板环节序列） */
    @Column(name = "claim_stage", nullable = false, length = 64)
    private String claimStage;

    /** 环节处理时限（小时） */
    @Column(name = "limit_hours", nullable = false)
    private Integer limitHours;

    /** 预警时限（小时，0=不预警） */
    @Column(name = "alert_hours")
    private Integer alertHours;
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
