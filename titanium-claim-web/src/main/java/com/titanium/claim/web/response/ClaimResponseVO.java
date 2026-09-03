package com.titanium.claim.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.metadata.enums.claim.ClaimEnum;

import lombok.Data;

/**
 * 理赔案件响应VO
 * <p>
 * 用于返回Web层理赔案件的响应数据
 * </p>
 */
@Data
public class ClaimResponseVO {
    /**
     * 理赔案件ID
     */
    private String claimId;

    /**
     * 客户ID
     */
    private String customerId;

    /**
     * 保单ID
     */
    private String policyId;

    /**
     * 理赔编号
     */
    private String claimNumber;

    /**
     * 理赔类型
     */
    private ClaimEnum.ClaimType claimType;

    /**
     * 事故日期
     */
    private LocalDateTime incidentDate;

    /**
     * 事故描述
     */
    private String incidentDescription;

    /**
     * 理赔金额
     */
    private BigDecimal claimAmount;

    /**
     * 理赔状态
     */
    private ClaimStatus status;

    /**
     * 理赔处理阶段码（REPORT/SURVEY/LOSS_ASSESS/APPROVAL/...）
     */
    private String phase;

    /**
     * 核定赔付金额（结算后填充）
     */
    private BigDecimal settledAmount;

    /**
     * 赔付状态码（PROCESSING/SUCCESS/FAILED/CLOSED/REJECTED_CLOSED，结算后填充）
     */
    private String paymentStatus;

    /**
     * 赔付状态中文描述
     */
    private String paymentStatusDescription;

    /**
     * 支付单号（支付域出账成功回写）
     */
    private String paymentNo;

    /**
     * 拒赔原因编码（RejectReason code，拒赔时记录）
     */
    private String rejectionReason;

    /**
     * 拒赔时间
     */
    private LocalDateTime rejectedAt;

    /**
     * 结案时间
     */
    private LocalDateTime closedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
