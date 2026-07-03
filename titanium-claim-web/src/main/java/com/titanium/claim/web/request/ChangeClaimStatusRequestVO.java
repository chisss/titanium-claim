package com.titanium.claim.web.request;

import com.titanium.claim.common.enums.ClaimStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 变更理赔案件状态请求VO
 * <p>
 * 用于接收Web层变更理赔案件状态的请求参数
 * </p>
 */
@Data
public class ChangeClaimStatusRequestVO {
    @NotNull(message = "新状态不能为空")
    private ClaimStatus newStatus;

    @NotBlank(message = "变更原因不能为空")
    private String reason;
}
