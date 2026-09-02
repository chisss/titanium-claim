package com.titanium.claim.application.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 理赔案件应用层读模型
 * <p>
 * application 读用例出参：由应用服务从 CQRS 读模型（{@code ClaimQueryResult}/{@code t_claim_view}）
 * 经 {@code ClaimReadModelMapper} 声明式组装（状态/理赔类型枚举收敛为 code 与中文描述）、屏蔽 domain
 * 聚合根，供表现层（web mapper）映射为对外响应/展示对象。<b>非对外远程契约</b>（对外契约是 api 层
 * {@code ClaimResponse}），故不带 DTO 后缀、不置于 api 层，避免与「DTO=对外契约」语义混淆。
 * </p>
 */
@Data
public class ClaimReadModel {
    private String        claimId;
    private String        customerId;
    private String        policyId;
    private String        claimNumber;
    /** 理赔类型码（枚举收敛为 code） */
    private String        claimType;
    private LocalDateTime incidentDate;
    private String        incidentDescription;
    private BigDecimal    claimAmount;
    /** 理赔状态码（枚举收敛为 code） */
    private String        status;
    /** 理赔状态中文描述 */
    private String        statusDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
