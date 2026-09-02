package com.titanium.claim.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 给付金额来源规则枚举（本域专属，置于 claim-common/enums 统一管理）
 * <p>
 * 给付金额精算的来源分级（红线 20：落库描述枚举化，持久化存 code）：
 * 定额给付取保单基本保额（M1）；全残给付按产品条款取
 * {@code max(账户价值, 基本保额)}（ACCOUNT_VALUE_MAX，M3/CLAIM-6）；报销型理算公式（FORMULA）
 * 后续按产品条款演进追加。
 * </p>
 */
@Getter
public enum BenefitSource implements BaseEnum {
    /** 基本保额定额给付（身故/全残/重疾定额给付） */
    BASIC_SUM_INSURED(1, "BASIC_SUM_INSURED", "基本保额定额给付"),
    /** 账户价值与基本保额孰高给付（全残给付条款来源，账户价值缺省回落基本保额） */
    ACCOUNT_VALUE_MAX(2, "ACCOUNT_VALUE_MAX", "账户价值与基本保额孰高给付");

    BenefitSource(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    private final Integer enumCode;
    private final String  code;
    private final String  name;
}
