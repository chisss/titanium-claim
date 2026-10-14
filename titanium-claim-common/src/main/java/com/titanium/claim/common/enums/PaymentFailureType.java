package com.titanium.claim.common.enums;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/**
 * 赔付失败类型（支付域出账未成功回写）
 * <p>
 * 取值镜像 payment 域出站载荷的 {@code resultType}（对端 {@code PaymentOrderStatus} 的 code）：
 * 跨域契约以 code 传递，本域独立定义，**不依赖对端域类型**（防腐）。
 * </p>
 */
@Getter
public enum PaymentFailureType implements BaseEnum {

    /** 渠道确认失败：外部支付渠道返回失败（如账户异常、余额不足） */
    FAILED(1, "FAILED", "渠道确认失败"),

    /** 人工取消：出款在渠道成功前被内部主动取消 */
    @Reserved("2026-10-14 g16-05 零引用登记（枚举级消费）：存在 values/valueOf/自定方法/:: 调用，常量名零出现；接入时摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    CANCELLED(2, "CANCELLED", "人工取消");

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    PaymentFailureType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 按对端 code 反查。
     * <p>
     * 未匹配返回 {@code null} 而非抛异常：对端新增类型时本域应降级为「类型未知」继续回写，
     * 不能因一个展示维度不认识就把整条赔付失败回写链路卡死。
     * </p>
     */
    public static PaymentFailureType fromCode(String code) {
        return BaseEnum.fromCode(PaymentFailureType.class, code);
    }
}
