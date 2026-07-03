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
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
