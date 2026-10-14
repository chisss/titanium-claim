package com.titanium.claim.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.Reserved;

import lombok.Getter;

/**
 * 黑名单标的类型枚举（本域专属，claim-common/enums/config 统一管理）
 * <p>
 * 理赔配置子域黑名单（{@code ClaimBlacklist}）的标的维度，覆盖产品文档 §4.2
 * 「人/车/医院/修理厂黑名单」，持久化存 code（红线 20）。
 * </p>
 */
@Getter
public enum BlacklistSubjectType implements BaseEnum {
    /** 人员：客户/受益人/驾驶员 */
    @Reserved("2026-10-14 g16-05 零引用登记（枚举级消费）：存在 values/valueOf/自定方法/:: 调用，常量名零出现；接入时摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    PERSON(1, "PERSON", "人员"),
    /** 车辆：车牌/车架号 */
    @Reserved("2026-10-14 g16-05 零引用登记（枚举级消费）：存在 values/valueOf/自定方法/:: 调用，常量名零出现；接入时摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    VEHICLE(2, "VEHICLE", "车辆"),
    /** 医院：恶意骗保/虚开医院 */
    @Reserved("2026-10-14 g16-05 零引用登记（枚举级消费）：存在 values/valueOf/自定方法/:: 调用，常量名零出现；接入时摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    HOSPITAL(3, "HOSPITAL", "医院"),
    /** 修理厂：车险虚报维修 */
    @Reserved("2026-10-14 g16-05 零引用登记（枚举级消费）：存在 values/valueOf/自定方法/:: 调用，常量名零出现；接入时摘标注+删清单行。见 docs/技术文档/枚举零引用清单.md")
    REPAIR_SHOP(4, "REPAIR_SHOP", "修理厂");

    BlacklistSubjectType(Integer enumCode, String code, String name) {
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
     * @return 标的类型枚举
     */
    public static BlacklistSubjectType fromCode(String code) {
        return BaseEnum.fromCode(BlacklistSubjectType.class, code);
    }
}
