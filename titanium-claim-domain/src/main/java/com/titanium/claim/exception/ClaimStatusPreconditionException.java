package com.titanium.claim.exception;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 理赔状态不满足操作前置条件异常
 * <p>
 * 当理赔案件的当前状态不满足某操作的前置条件时抛出，如：
 * <ul>
 *   <li>核赔结算要求状态为 APPROVED</li>
 *   <li>案件关闭要求状态为 PAID 或 REJECTED</li>
 * </ul>
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class ClaimStatusPreconditionException extends DomainException {

    public ClaimStatusPreconditionException(ClaimId claimId, ClaimStatus currentStatus,
                                            String operation, String requiredStatus) {
        super(ClaimErrorCode.CLAIM_STATUS_PRECONDITION_NOT_MET,
              String.format("理赔案件[%s] 操作 %s 要求状态为 %s，当前状态: %s",
                            claimId.value(), operation, requiredStatus, currentStatus.name()));
    }

    /**
     * 防重复操作工厂：案件已进入赔付流程时禁止重复结算/给付（结算后状态保持 APPROVED
     * 待支付域回写，须以赔付状态而非案件状态拦截重复指令）。
     */
    public static ClaimStatusPreconditionException alreadySettled(ClaimId claimId, String operation) {
        return new ClaimStatusPreconditionException(String.format(
                "理赔案件[%s] 已进入赔付流程，禁止重复执行「%s」", claimId.value(), operation));
    }

    /** 携带自定义消息的构造器（用于防重复等非「状态不满足」场景） */
    private ClaimStatusPreconditionException(String message) {
        super(ClaimErrorCode.CLAIM_STATUS_PRECONDITION_NOT_MET, message);
    }
}
