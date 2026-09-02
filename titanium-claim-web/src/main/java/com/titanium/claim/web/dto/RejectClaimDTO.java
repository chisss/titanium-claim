package com.titanium.claim.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 拒赔 DTO（web 前端入参，核赔否决 PENDING/PROCESSING → REJECTED）
 * <p>
 * 拒赔原因按枚举 code 承载（NOT_IN_COVERAGE/WAITING_PERIOD/INSUFFICIENT_EVIDENCE/FRAUD_SUSPECTED/
 * UNTRUTHFUL_DISCLOSURE/UNPAID_PREMIUM/OTHER），禁止裸字符串（红线 20）。
 * </p>
 */
@Data
public class RejectClaimDTO {

    /** 拒赔原因枚举 code */
    @NotBlank(message = "拒赔原因不能为空")
    private String reasonCode;

    /** 拒赔意见说明 */
    private String comment;
}
