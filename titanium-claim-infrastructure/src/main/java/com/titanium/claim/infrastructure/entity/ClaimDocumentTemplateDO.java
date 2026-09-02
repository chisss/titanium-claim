package com.titanium.claim.infrastructure.entity;

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
 * 单证模板持久化对象（理赔配置支撑子域，状态存储）
 * <p>
 * 映射表 {@code t_claim_document_template}。聚合根 {@code ClaimDocumentTemplate} 与 DO 的转换经
 * {@code ClaimConfigPersistenceMapper}（MapStruct），材料清单落库 JSON 文本。
 * </p>
 */
@Entity
@Table(name = "t_claim_document_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_claim_document_template_biz",
                columnNames = {"tenant_id", "insurance_line", "claim_type"}),
        indexes = @Index(name = "idx_claim_document_template_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClaimDocumentTemplateDO extends BaseEntity {

    /** 模板ID（雪花，主键） */
    @Id
    @Column(name = "template_id", nullable = false, length = 32)
    private String templateId;

    /** 险种线 code（metadata InsuranceProductType） */
    @Column(name = "insurance_line", nullable = false, length = 32)
    private String insuranceLine;

    /** 理赔类型 code（metadata ClaimEnum.ClaimType） */
    @Column(name = "claim_type", nullable = false, length = 50)
    private String claimType;

    /** 必填材料清单（JSON 数组文本） */
    @Column(name = "required_documents", columnDefinition = "TEXT")
    private String requiredDocuments;

    /** 选填材料清单（JSON 数组文本） */
    @Column(name = "optional_documents", columnDefinition = "TEXT")
    private String optionalDocuments;
}
