package com.titanium.claim.web.dto;

import com.titanium.claim.common.enums.ClaimStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 变更理赔案件状态 DTO（web 前端入参）
 * <p>
 * 用于接收后台/端上变更理赔案件状态的请求参数。
 * </p>
 */
@Data
public class ChangeClaimStatusDTO {
    @NotNull(message = "新状态不能为空")
    private ClaimStatus newStatus;

    @NotBlank(message = "变更原因不能为空")
    private String reason;
}
