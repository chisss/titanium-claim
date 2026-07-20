package com.titanium.claim.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.metadata.enums.claim.ClaimEnum;

import lombok.Data;

/**
 * 理赔案件应用层读模型
 * <p>
 * application 读用例出参：由应用服务从 CQRS 读模型（{@code ClaimQueryResult}/{@code t_claim_view}）
 * 组装、屏蔽 domain 聚合根，供表现层（web mapper）映射为对外响应/展示对象。<b>非对外远程契约</b>
 * （对外契约是 api 层 {@code ClaimResponse}），故不带 DTO 后缀、不置于 api 层，避免与「DTO=对外契约」
 * 语义混淆。
 * </p>
 */
@Data
public class ClaimReadModel {
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

    /** 状态枚举 → 状态码 + 描述（空安全） */
    public void setStatus(ClaimStatus status) {
        if (status != null) {
            this.status = status.getCode();
            this.statusDescription = status.getName();
        }
    }

    /** 理赔类型枚举 → 类型码（空安全） */
    public void setClaimType(ClaimEnum.ClaimType claimType) {
        if (claimType != null) {
            this.claimType = claimType.getCode();
        }
    }
}
