package com.titanium.claim.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeClaimStatusRequestDTO {
    @NotBlank(message = "新状态不能为空")
    @Size(max = 20, message = "状态不能超过20个字符")
    private String newStatus;

    @NotBlank(message = "原因不能为空")
    @Size(max = 255, message = "原因不能超过255个字符")
    private String reason;
}