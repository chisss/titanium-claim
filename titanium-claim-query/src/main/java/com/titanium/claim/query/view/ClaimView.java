package com.titanium.claim.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.common.jpa.BaseView;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 理赔案件读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_claim_view}，与写侧事件存储物理隔离。 由
 * {@link com.titanium.claim.query.handler.projection.ClaimProjectionEventHandler} 订阅领域事件投影而来。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间（投影时间）、乐观锁版本等读模型公共字段。
 * </p>
 */
@Entity
@Table(name = "t_claim_view")
@Getter
@Setter
public class ClaimView extends BaseView {

    /** 理赔案件ID（聚合根ID，读模型主键） */
    @Id
    @Column(name = "claim_id", nullable = false, length = 36)
    private String              claimId;

    /** 客户ID */
    @Column(name = "customer_id", length = 36)
    private String              customerId;

    /** 保单ID */
    @Column(name = "policy_id", length = 36)
    private String              policyId;

    /** 理赔编号 */
    @Column(name = "claim_number", length = 50)
    private String              claimNumber;

    /** 理赔类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", length = 50)
    private ClaimEnum.ClaimType claimType;

    /** 出险日期 */
    @Column(name = "incident_date")
    private LocalDateTime       incidentDate;

    /** 出险描述 */
    @Column(name = "incident_description", columnDefinition = "TEXT")
    private String              incidentDescription;

    /** 理赔金额 */
    @Column(name = "claim_amount", precision = 15, scale = 2)
    private BigDecimal          claimAmount;

    /** 理赔状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ClaimStatus         status;

    /** 理赔处理阶段 */
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 20)
    private ClaimPhase          phase;

    /** 核定赔付金额（结算后填充） */
    @Column(name = "settled_amount", precision = 15, scale = 2)
    private BigDecimal          settledAmount;

    /** 赔付状态（结算后赔付中，支付域出账成功回写为成功） */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private ClaimEnum.PaymentStatus paymentStatus;

    /** 支付单号（支付域出账成功回写） */
    @Column(name = "payment_no", length = 50)
    private String              paymentNo;

    /** 拒赔原因编码（RejectReason code，拒赔时记录） */
    @Column(name = "rejection_reason", length = 50)
    private String              rejectionReason;

    /** 拒赔时间 */
    @Column(name = "rejected_at")
    private LocalDateTime       rejectedAt;

    /** 结案时间 */
    @Column(name = "closed_at")
    private LocalDateTime       closedAt;

    /** 反欺诈警示与统计口径标记（AlertType code 逗号分隔，快赔通道判据的数据来源） */
    @Column(name = "alert_flags", length = 200)
    private String              alertFlags;
}
