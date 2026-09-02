package com.titanium.claim.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.common.exception.InvalidClaimAmountException;
import com.titanium.claim.common.exception.InvalidClaimStatusException;
import com.titanium.claim.valueobject.CoverageResult;
import com.titanium.claim.valueobject.CoverageResult.CoverageMatch;
import com.titanium.claim.valueobject.RuleDecision;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.AllArgsConstructor;

/**
 * 理赔领域服务（纯规则，无 Port / 无命令 / 无基础设施依赖，§3.4.4 三无判据）
 * <p>
 * 只承载不属于任何单个聚合根的跨聚合纯业务计算：理赔金额/状态判定、理赔编号生成、
 * 责任匹配判定（出险要素 × 条款责任摘要）。取数（clause/保单）与发命令均属 application 编排。
 * </p>
 */
@Service
@AllArgsConstructor
public class ClaimService {

    /**
     * 验证理赔金额是否有效
     * @param amount 理赔金额
     */
    public void validateClaimAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidClaimAmountException();
        }
    }

    /**
     * 验证理赔状态是否有效
     * @param status 理赔状态
     */
    public void validateClaimStatus(String status) {
        try {
            ClaimStatus.fromCode(status);
        } catch (Exception e) {
            throw new InvalidClaimStatusException();
        }
    }

    /**
     * 生成理赔编号
     * @return 理赔编号
     */
    public String generateClaimNumber() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("CLAIM-%04d%02d%02d-%06d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                (int) (Math.random() * 1000000));
    }

    /**
     * 责任匹配判定（CLAIM-4）：出险是否在保单条款保险责任范围内
     * <p>
     * 纯规则四结论（产品文档 §2.7）：
     * <ol>
     *   <li>无条款责任数据 → 责任除外（{@code ClaimErrorCode.CLAIM_OUT_OF_COVERAGE}）；</li>
     *   <li>命中责任但出险日期在等待期内（非意外）→ 需人工判定（按条款退保费/现金价值走人工核赔）；</li>
     *   <li>意外理赔（ACCIDENT）不受等待期约束 → 责任成立；</li>
     *   <li>其余命中责任且已过等待期 → 责任成立。</li>
     * </ol>
     * 条款取数由 application 编排（ClauseServicePort），本方法零 Port 依赖，可脱离容器 new 直测。
     * </p>
     *
     * @param claimType       理赔类型（意外理赔豁免等待期）
     * @param incidentDate    出险日期
     * @param coverageResult  责任匹配结果（保单生效日期 + 条款责任摘要，application 编排装配）
     * @return 判定结论（责任成立 / 责任除外 / 需人工判定）
     */
    public RuleDecision isClaimInCoverage(ClaimEnum.ClaimType claimType, LocalDateTime incidentDate,
                                          CoverageResult coverageResult) {
        if (coverageResult.matches().isEmpty()) {
            return RuleDecision.rejected(ClaimErrorCode.CLAIM_OUT_OF_COVERAGE,
                    claimType == null ? "未知" : claimType.getCode());
        }
        // 等待期校验：意外理赔豁免；出险日期在生效日期+等待期内 → 需人工判定
        for (CoverageMatch match : coverageResult.matches()) {
            int waitingPeriodDays = match.waitingPeriodDays() == null ? 0 : match.waitingPeriodDays();
            boolean withinWaitingPeriod = waitingPeriodDays > 0 && incidentDate != null
                    && incidentDate.isBefore(coverageResult.policyEffectiveDate().plusDays(waitingPeriodDays));
            if (withinWaitingPeriod && claimType != ClaimEnum.ClaimType.ACCIDENT) {
                return RuleDecision.manualReview();
            }
        }
        return RuleDecision.approved();
    }
}
