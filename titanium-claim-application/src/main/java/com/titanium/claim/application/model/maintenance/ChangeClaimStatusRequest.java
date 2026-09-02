package com.titanium.claim.application.model.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 变更理赔状态请求（application 写用例入参）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeClaimStatusRequest {
    @NotBlank(message = "新状态不能为空")
    @Size(max = 20, message = "状态不能超过20个字符")
    private String newStatus;

    @NotBlank(message = "原因不能为空")
    @Size(max = 255, message = "原因不能超过255个字符")
    private String reason;
}
