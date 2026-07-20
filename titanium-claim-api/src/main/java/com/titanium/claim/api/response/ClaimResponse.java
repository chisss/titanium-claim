package com.titanium.claim.api.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 理赔响应（对外契约，Feign 出参）
 * <p>
 * 面向其它微服务返回理赔案件详情，领域枚举以 String 码值承载。
 * </p>
 */
@Data
public class ClaimResponse {
    private String claimId;
    private String customerId;
    private String policyId;
    private String claimNumber;
    private String claimType;
    private LocalDateTime incidentDate;
    private String incidentDescription;
    private BigDecimal claimAmount;
    private String status;
    private String statusDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
