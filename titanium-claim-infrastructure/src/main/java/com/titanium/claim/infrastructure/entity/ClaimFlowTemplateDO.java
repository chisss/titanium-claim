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
 * 流程模板持久化对象（理赔配置支撑子域，状态存储）
 * <p>
 * 映射表 {@code t_claim_flow_template}。聚合根 {@code ClaimFlowTemplate} 与 DO 的转换经
 * {@code ClaimConfigPersistenceMapper}（MapStruct），环节序列/时限等集合字段落库 JSON 文本。
 * </p>
 */
@Entity
@Table(name = "t_claim_flow_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_claim_flow_template_biz",
                columnNames = {"tenant_id", "insurance_line", "claim_type"}),
        indexes = @Index(name = "idx_claim_flow_template_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClaimFlowTemplateDO extends BaseEntity {
    /** 主键（雪花，独立于业务ID列） */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;


    /** 模板ID（雪花，主键） */
        @Column(name = "template_id", nullable = false, length = 32)
    private String templateId;

    /** 险种线 code（metadata InsuranceProductType） */
    @Column(name = "insurance_line", nullable = false, length = 32)
    private String insuranceLine;

    /** 案件类型 code（metadata ClaimEnum.ClaimType） */
    @Column(name = "claim_type", nullable = false, length = 50)
    private String claimType;

    /** 环节序列（JSON 数组文本） */
    @Column(name = "stage_sequence", nullable = false, columnDefinition = "TEXT")
    private String stageSequence;

    /** 各环节时限小时数（JSON 对象文本：环节名→小时） */
    @Column(name = "stage_time_limits", columnDefinition = "TEXT")
    private String stageTimeLimits;

    /** 责任角色 */
    @Column(name = "responsible_role", length = 64)
    private String responsibleRole;

    /** 必经校验点（JSON 数组文本） */
    @Column(name = "mandatory_checkpoints", columnDefinition = "TEXT")
    private String mandatoryCheckpoints;
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
