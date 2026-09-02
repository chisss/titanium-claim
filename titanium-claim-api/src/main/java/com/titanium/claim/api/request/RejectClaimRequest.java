package com.titanium.claim.api.request;

import lombok.Data;

/**
 * 拒赔请求（对外契约，Feign 入参，核赔否决 PENDING/PROCESSING → REJECTED）
 * <p>
 * 拒赔原因按枚举 code 承载（NOT_IN_COVERAGE/WAITING_PERIOD/FRAUD_SUSPECTED/...），
 * 禁止裸字符串。
 * </p>
 */
@Data
public class RejectClaimRequest {

    /** 拒赔原因枚举 code */
    private String reasonCode;
    /** 拒赔意见说明 */
    private String comment;
}
