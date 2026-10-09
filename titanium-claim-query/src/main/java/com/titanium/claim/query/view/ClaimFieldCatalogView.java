package com.titanium.claim.query.view;

import java.time.LocalDate;

import com.titanium.common.jpa.BaseView;
import com.titanium.metadata.enums.claim.fieldcatalog.ClaimFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户字段目录行（**一行 = 一个稳定字段**），映射 {@code t_claim_field_catalog}。
 *
 * <p>与其它读模型的区别：本表**不是事件投影的产物**，而是平台标准元数据表——内容由 starter 在
 * {@code seed-mode=init} 导入（{@code g08-06}），不随业务事件变化。故本类没有对应的
 * {@code XxxProjectionEventHandler}，这一点是<b>有意设计</b>而非遗漏。</p>
 *
 * <p>列与 {@code ClaimFieldDescriptor} 的 11 个组件一一对应（能力展开为 6 列、分析位展开为 4 列），
 * 故领域目录可从本表**无损重建**；列与组件错位会让描述符构造器的不变量
 * （集合⇔对象标识、敏感⇒掩码非 NONE、可清空⇒可提案，以及四条分析位不变量）在读取侧静默失效。</p>
 *
 * <p>{@code tenantId} 由 {@link BaseView} 承载且不参与过滤——目录是**跨租户共享的平台元数据**
 * （{@code G07/AC-10}），租户差异落在特征定义层。</p>
 */
@Entity
@Table(name = "t_claim_field_catalog")
@Getter
@Setter
public class ClaimFieldCatalogView extends BaseView {

    /** 稳定字段编码（目录主键，业务唯一键） */
    @Id
    @Column(name = "field_code", nullable = false, length = 64)
    private String fieldCode;

    /** 字段所属业务对象类型（本域专属枚举） */
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 32)
    private ClaimFieldObjectType objectType;

    /** 字段值类型（跨域共享枚举） */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 32)
    private PolicyFieldValueType valueType;

    /** 国际化标签键 */
    @Column(name = "label_key", nullable = false, length = 128)
    private String labelKey;

    /** 是否属于集合对象 */
    @Column(name = "collection", nullable = false)
    private boolean collection;

    /** 集合对象稳定标识字段（非集合字段为 null） */
    @Column(name = "object_identity_field", length = 64)
    private String objectIdentityField;

    /** 是否允许读取 */
    @Column(name = "readable", nullable = false)
    private boolean readable;

    /** 是否允许提交变更提案 */
    @Column(name = "proposable", nullable = false)
    private boolean proposable;

    /** 是否允许清空 */
    @Column(name = "clearable", nullable = false)
    private boolean clearable;

    /** 是否已具备真实执行映射 */
    @Column(name = "execution_supported", nullable = false)
    private boolean executionSupported;

    /** 是否必须指定稳定业务对象ID */
    @Column(name = "requires_object_id", nullable = false)
    private boolean requiresObjectId;

    /** 业务变更类别编码（仅可提案字段非空） */
    @Column(name = "change_type_code", length = 64)
    private String changeTypeCode;

    /** 敏感级别（跨域共享枚举） */
    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity", nullable = false, length = 32)
    private PolicyFieldSensitivityLevel sensitivity;

    /** 掩码策略（跨域共享枚举） */
    @Enumerated(EnumType.STRING)
    @Column(name = "masking_policy", nullable = false, length = 32)
    private PolicyFieldMaskingPolicy maskingPolicy;

    /** 废弃日期（未废弃为 null） */
    @Column(name = "deprecated_at")
    private LocalDate deprecatedAt;

    /** 是否参与分析（总开关；为 false 时其余三位必须为 false） */
    @Column(name = "analyzable", nullable = false)
    private boolean analyzable;

    /** 是否可作为聚合指标（TEXT/OBJECT/ARRAY 不得为 true） */
    @Column(name = "aggregatable", nullable = false)
    private boolean aggregatable;

    /** 是否可作为分组维度（collection=true 时必须为 true） */
    @Column(name = "groupable", nullable = false)
    private boolean groupable;

    /** 是否可作为筛选条件（groupable=true 时必须为 true） */
    @Column(name = "filterable", nullable = false)
    private boolean filterable;
}
