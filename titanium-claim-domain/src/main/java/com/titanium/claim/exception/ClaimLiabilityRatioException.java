package com.titanium.claim.exception;

import java.math.BigDecimal;

import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 定损责任比例量纲异常
 * <p>
 * {@link LossAssessment} 的责任比例契约口径是 <b>0-1 的小数</b>（全责 1.0、同责 0.5），
 * 而人机终端习惯按 <b>百分数</b> 录入（80 表示 80%）—— 两个各自自洽的约定拼在一起差 100 倍。
 * 该比例直接决定核定赔付金额（=（定损总金额−残值）×责任比例），而后者是核赔结算的
 * <b>唯一权威</b>金额（{@code Claim.resolveSettledAmount} 采信之），会经支付域真实出账。
 * </p>
 * <p>
 * 故在值对象构造处 <b>显式拒绝越界值</b>：量纲错误必须在边界处失败，
 * 不能静默放大为 100 倍赔款。参考 D-501-49。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/16
 */
public class ClaimLiabilityRatioException extends DomainException {

    private ClaimLiabilityRatioException(ClaimErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 责任比例越出 [0,1] 区间（典型成因：调用方按百分数传入）。
     *
     * @param liabilityRatio 调用方传入的责任比例
     * @return 异常
     */
    public static ClaimLiabilityRatioException outOfRange(BigDecimal liabilityRatio) {
        return new ClaimLiabilityRatioException(ClaimErrorCode.CLAIM_LIABILITY_RATIO_INVALID,
                String.format("定损责任比例[%s]超出合法区间[0,1]：应按小数传递（全责 1.0、同责 0.5），"
                        + "若为百分数须先除以 100", liabilityRatio.toPlainString()));
    }
}
