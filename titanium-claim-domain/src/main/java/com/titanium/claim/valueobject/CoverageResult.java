package com.titanium.claim.valueobject;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 责任匹配结果值对象（领域服务入参，application 编排经 Port 取数后装配）
 * <p>
 * 承载「保单生效日期 + 条款保险责任摘要列表」，供 {@code ClaimService.isClaimInCoverage}
 * 纯规则判定出险是否在责任范围内。与 {@code ClauseServicePort.CoverageInfo}（Port 出参）
 * 分离：本值对象是领域服务入参契约，application 编排负责翻译装配，领域服务零 Port 依赖
 * （§3.4.4 领域服务纯净性 ArchUnit 断言）。
 * </p>
 *
 * @param policyEffectiveDate 保单生效日期（等待期起算基准）
 * @param matches             条款保险责任摘要列表（跨条款合并，可为空）
 */
public record CoverageResult(
        LocalDateTime policyEffectiveDate,
        List<CoverageMatch> matches
) {

    /** 紧凑构造器：保单生效日期必填（等待期计算基准） */
    public CoverageResult {
        if (policyEffectiveDate == null) {
            throw new IllegalArgumentException("保单生效日期不能为空");
        }
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    /**
     * 单条保险责任匹配摘要（CoverageResponse 的领域侧镜像，仅含责任判定要素）
     * <p>
     * 条款域按有效条款过滤返回，取到即视为有效责任。
     * </p>
     *
     * @param coverageId        责任ID
     * @param coverageCode      责任编码
     * @param coverageName      责任名称
     * @param waitingPeriodDays 等待期天数（0/null 表示无等待期）
     */
    public record CoverageMatch(
            String coverageId,
            String coverageCode,
            String coverageName,
            Integer waitingPeriodDays
    ) {
    }
}
