package com.titanium.claim.infrastructure.entity;

import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 黑名单持久化对象（理赔配置支撑子域，状态存储）
 * <p>
 * 映射表 {@code t_claim_blacklist}。聚合根 {@code ClaimBlacklist} 与 DO 的转换经
 * {@code ClaimConfigPersistenceMapper}（MapStruct），标的类型/状态枚举落库 code。
 * </p>
 */
@Entity
@Table(name = "t_claim_blacklist",
        indexes = {
                @Index(name = "idx_claim_blacklist_tenant", columnList = "tenant_id"),
                @Index(name = "idx_claim_blacklist_subject",
                        columnList = "tenant_id,subject_type,subject_id,status")
        })
@Getter
@Setter
@NoArgsConstructor
public class ClaimBlacklistDO extends BaseEntity {
    /** 主键（雪花，独立于业务ID列） */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;


    /** 黑名单ID（雪花，主键） */
        @Column(name = "blacklist_id", nullable = false, length = 32)
    private String blacklistId;

    /** 标的类型 code（BlacklistSubjectType） */
    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    /** 标的主键（人员ID/车牌/医院ID/修理厂ID） */
    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    /** 标的名称（展示用） */
    @Column(name = "subject_name", length = 128)
    private String subjectName;

    /** 拉黑原因 code（业务枚举） */
    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    /** 生效状态 code（BlacklistStatus） */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 生效时间 */
    @Column(name = "effective_time")
    private LocalDateTime effectiveTime;
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
