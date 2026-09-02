package com.titanium.claim.application.model.maintenance;

import lombok.Data;

/**
 * 拒赔请求（application 写用例入参，核赔否决 PENDING/PROCESSING → REJECTED）
 * <p>
 * 拒赔原因必须携带 {@code RejectReason} 枚举 code（红线 20：业务描述禁止裸字符串），
 * 由应用层门面经 {@code RejectReason.fromCode} 还原后装配 {@code RejectClaimCommand}。
 * </p>
 */
@Data
public class RejectClaimRequest {

    /** 拒赔原因枚举 code（NOT_IN_COVERAGE/WAITING_PERIOD/INSUFFICIENT_EVIDENCE/FRAUD_SUSPECTED/...） */
    private String reasonCode;
    /** 拒赔意见说明 */
    private String comment;
}
