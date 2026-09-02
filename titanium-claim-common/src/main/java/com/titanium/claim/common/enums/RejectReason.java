package com.titanium.claim.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 拒赔原因枚举（本域专属，置于 claim-common/enums 统一管理）
 * <p>
 * 拒赔必须携带枚举化原因（红线 20：业务描述禁止写死中文字符串），
 * 持久化存 code，展示层按语言渲染。
 * </p>
 */
@Getter
public enum RejectReason implements BaseEnum {
    /** 保险责任除外（出险不在保障责任范围） */
    NOT_IN_COVERAGE(1, "NOT_IN_COVERAGE", "保险责任除外"),
    /** 等待期内出险 */
    WAITING_PERIOD(2, "WAITING_PERIOD", "等待期内出险"),
    /** 单证不齐或无法证明事故真实性 */
    INSUFFICIENT_EVIDENCE(3, "INSUFFICIENT_EVIDENCE", "单证不齐或无法证明事故真实性"),
    /** 欺诈嫌疑 */
    FRAUD_SUSPECTED(4, "FRAUD_SUSPECTED", "欺诈嫌疑"),
    /** 投保未如实告知 */
    UNTRUTHFUL_DISCLOSURE(5, "UNTRUTHFUL_DISCLOSURE", "投保未如实告知"),
    /** 保费未缴清 */
    UNPAID_PREMIUM(6, "UNPAID_PREMIUM", "保费未缴清"),
    /** 其他原因 */
    OTHER(99, "OTHER", "其他原因");

    /**
     * 持久化数字码
     */
    private final Integer enumCode;

    /**
     * 业务编码
     */
    private final String code;

    /**
     * 中文名称
     */
    private final String name;

    RejectReason(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    /**
     * 按 code 反查拒赔原因，未匹配返回 OTHER 兜底。
     *
     * @param code 拒赔原因码
     * @return 匹配的枚举（未知 code 返回 OTHER）
     */
    public static RejectReason fromCode(String code) {
        RejectReason reason = BaseEnum.fromCode(RejectReason.class, code);
        return reason == null ? OTHER : reason;
    }

    @Override
    public String toString() {
        return code;
    }
}
