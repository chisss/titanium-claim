package com.titanium.claim.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 责任判定结论枚举（本域专属，置于 claim-common/enums 统一管理）
 * <p>
 * {@code ClaimService.isClaimInCoverage} 领域服务的规则结论四态（产品文档 §2.7 CLAIM-4）：
 * 责任成立 / 责任除外 / 等待期内 / 需人工判定。持久化存 code（红线 20）。
 * </p>
 */
@Getter
public enum DecisionType implements BaseEnum {
    /** 责任成立：出险命中有效保险责任且已过等待期（意外理赔不受等待期约束） */
    APPROVED(1, "APPROVED", "责任成立"),
    /** 责任除外：出险未命中任何有效保险责任 */
    REJECTED(2, "REJECTED", "责任除外"),
    /** 需人工判定：等待期内非意外出险或责任数据缺失，转人工核赔按条款处理 */
    MANUAL_REVIEW(3, "MANUAL_REVIEW", "需人工判定");

    DecisionType(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }

    private final Integer enumCode;
    private final String  code;
    private final String  name;
}
