package com.titanium.claim.common.exception;

import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 身故给付核算非法异常
 * <p>
 * 当身故给付金核算值对象的不变量被破坏时抛出，如给付总额非正数、受益人份额缺失、
 * 份额之和与给付总额不一致（分配守恒）。错误码按具体校验语义从 {@link ClaimErrorCode}
 * 选取（金额无效/份额缺失/份额不匹配）。
 * </p>
 */
public class BenefitCalculationException extends BusinessException {

    /**
     * 携带错误码与具体校验消息构造
     *
     * @param errorCode 身故给付校验错误码
     * @param message   具体校验失败消息
     */
    public BenefitCalculationException(ClaimErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
