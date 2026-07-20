package com.titanium.claim.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 理赔请求（对外契约，Feign 入参）
 * <p>
 * 用于创建与更新理赔案件的远程调用入参。api 契约自包含，领域枚举以 String 码值承载。
 * </p>
 */
@Data
public class ClaimRequest {
    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    @NotBlank(message = "保单ID不能为空")
    private String policyId;

    @NotBlank(message = "理赔类型不能为空")
    @Size(max = 50, message = "理赔类型不能超过50个字符")
    private String claimType;

    @NotNull(message = "事故日期不能为空")
    private LocalDateTime incidentDate;

    @NotBlank(message = "事故描述不能为空")
    private String incidentDescription;

    @NotNull(message = "理赔金额不能为空")
    private BigDecimal claimAmount;
}
