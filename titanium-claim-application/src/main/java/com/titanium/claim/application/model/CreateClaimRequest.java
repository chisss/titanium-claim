package com.titanium.claim.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建理赔案件请求（application 写用例入参）
 * <p>
 * application 用例入参模型：web {@code CreateClaimDTO} 与 api {@code ClaimRequest} 经
 * {@code ClaimWebMapper} 翻译收敛到本类型，作为应用服务稳定入口契约（非对外传输契约，故不带 DTO 后缀）。
 * </p>
 */
@Data
public class CreateClaimRequest {
    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    @NotBlank(message = "保单ID不能为空")
    private String policyId;

    @NotBlank(message = "理赔类型不能为空")
    @Size(max = 50, message = "理赔类型不能超过50个字符")
    private String claimType;

    @NotNull(message = "事故日期不能为空")
    @PastOrPresent(message = "事故日期不能为未来日期")
    private LocalDateTime incidentDate;

    @NotBlank(message = "事故描述不能为空")
    private String incidentDescription;

    @NotNull(message = "理赔金额不能为空")
    @DecimalMin(value = "0.01", message = "理赔金额必须大于0")
    @Digits(integer = 13, fraction = 2, message = "理赔金额格式不正确")
    private BigDecimal claimAmount;
}
