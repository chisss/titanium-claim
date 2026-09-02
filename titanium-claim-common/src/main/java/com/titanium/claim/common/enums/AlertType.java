package com.titanium.claim.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 理赔警示标记类型枚举（本域专属，置于 claim-common/enums 统一管理）
 * <p>
 * 反欺诈警示与统计口径标记（红线 20：落库描述枚举化，持久化存 code）：
 * 延迟报案/多次报案/风险评分属<b>欺诈警示类</b>（快赔通道判据：存在即拒绝自动核赔）；
 * 快赔标记是<b>统计口径标记</b>（快赔案件进入快赔统计，非欺诈信号）。
 * 快赔判据「无警示标记」仅排除前三者（见 {@code QuickPayOrchestrator}）。
 * </p>
 */
@Getter
public enum AlertType implements BaseEnum {

    /** 延迟报案（报案时间距出险时间超过 30 天，P1 自动风险评分） */
    LATE_REPORT(1, "LATE_REPORT", "延迟报案"),
    /** 多次报案（同保单 30 天内存在其他报案，P1 自动风险评分） */
    MULTIPLE_REPORTS(2, "MULTIPLE_REPORTS", "多次报案"),
    /** 风险评分（规则引擎风险评分命中警示，人工复核） */
    RISK_SCORE(3, "RISK_SCORE", "风险评分"),
    /** 快赔标记（小额快赔通道自动核赔案件，统计口径标记） */
    QUICK_PAY(4, "QUICK_PAY", "快赔");

    AlertType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    private final Integer enumCode;
    private final String  code;
    private final String  name;

    /**
     * 按稳定标识 code 反查枚举
     *
     * @param code 稳定标识
     * @return 警示标记类型枚举；未知 code 返回 {@code null}
     */
    public static AlertType fromCode(String code) {
        return BaseEnum.fromCode(AlertType.class, code);
    }
}
