package com.titanium.claim.valueobject;

import java.util.List;

import com.titanium.claim.common.enums.DecisionType;
import com.titanium.metadata.errorcode.BaseErrorCode;

/**
 * 规则判定结果值对象（领域服务出参，携带错误码枚举与动态参数）
 * <p>
 * 领域服务纯规则判定的统一结论载体（红线 19：结果对象 code 禁裸字符串，全量走错误码枚举）。
 * 拒绝结论由错误码枚举承载 message，动态值经 {@code args} 参数占位，边界层 MessageSource 按
 * {@code Accept-Language} 渲染。
 * </p>
 *
 * @param type       结论类型（责任成立/责任除外/需人工判定）
 * @param rejectCode 拒绝错误码（仅 REJECTED 结论携带，其余为 null）
 * @param args       错误码 message 动态参数（如出险日期、等待期天数）
 */
public record RuleDecision(
        DecisionType type,
        BaseErrorCode rejectCode,
        List<String> args
) {

    /** 责任成立 */
    public static RuleDecision approved() {
        return new RuleDecision(DecisionType.APPROVED, null, List.of());
    }

    /** 需人工判定（等待期内非意外/责任数据缺失，不阻断报案，转人工核赔） */
    public static RuleDecision manualReview() {
        return new RuleDecision(DecisionType.MANUAL_REVIEW, null, List.of());
    }

    /** 责任除外（携带错误码与动态参数） */
    public static RuleDecision rejected(BaseErrorCode errorCode, String... args) {
        return new RuleDecision(DecisionType.REJECTED, errorCode, List.of(args));
    }

    /** 是否责任成立 */
    public boolean isApproved() {
        return type == DecisionType.APPROVED;
    }

    /** 是否需人工判定 */
    public boolean isManualReview() {
        return type == DecisionType.MANUAL_REVIEW;
    }

    /** 是否责任除外 */
    public boolean isRejected() {
        return type == DecisionType.REJECTED;
    }
}
