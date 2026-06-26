package com.titanium.claim.application.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateClaimRequestDTO {
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