package com.titanium.claim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.common.enums.DecisionType;
import com.titanium.claim.valueobject.CoverageResult;
import com.titanium.claim.valueobject.CoverageResult.CoverageMatch;
import com.titanium.claim.valueobject.RuleDecision;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 理赔领域服务测试（CLAIM-4 责任匹配纯规则）
 * <p>
 * 验证 {@link ClaimService#isClaimInCoverage} 四结论：责任成立 / 责任除外 / 等待期内需人工判定 /
 * 意外理赔豁免等待期。纯领域服务可脱离容器 new 直测（§3.4.4 三无判据）。
 * </p>
 */
class ClaimServiceTest {

    private static final LocalDateTime EFFECTIVE = LocalDateTime.of(2026, 1, 1, 0, 0);

    private final ClaimService claimService = new ClaimService();

    private CoverageResult coverageWith(int waitingPeriodDays) {
        return new CoverageResult(EFFECTIVE, List.of(
                new CoverageMatch("COV-1", "COV-DEATH-01", "身故保险金", waitingPeriodDays)));
    }

    @Test
    @DisplayName("无条款责任数据 → 责任除外（CLAIM_OUT_OF_COVERAGE）")
    void shouldRejectWhenNoCoverage() {
        CoverageResult result = new CoverageResult(EFFECTIVE, List.of());

        RuleDecision decision = claimService.isClaimInCoverage(ClaimEnum.ClaimType.DEATH,
                LocalDateTime.of(2026, 6, 1, 0, 0), result);

        assertEquals(DecisionType.REJECTED, decision.type());
        assertEquals(ClaimErrorCode.CLAIM_OUT_OF_COVERAGE, decision.rejectCode());
    }

    @Test
    @DisplayName("命中责任且已过等待期 → 责任成立")
    void shouldApproveAfterWaitingPeriod() {
        RuleDecision decision = claimService.isClaimInCoverage(ClaimEnum.ClaimType.DEATH,
                LocalDateTime.of(2026, 6, 1, 0, 0), coverageWith(90));

        assertEquals(DecisionType.APPROVED, decision.type());
        assertTrue(decision.isApproved());
    }

    @Test
    @DisplayName("出险日在等待期内且非意外 → 需人工判定（转人工核赔）")
    void shouldManualReviewWithinWaitingPeriod() {
        RuleDecision decision = claimService.isClaimInCoverage(ClaimEnum.ClaimType.DEATH,
                LocalDateTime.of(2026, 2, 1, 0, 0), coverageWith(90));

        assertEquals(DecisionType.MANUAL_REVIEW, decision.type());
        assertTrue(decision.isManualReview());
    }

    @Test
    @DisplayName("出险日在等待期内但为意外理赔 → 豁免等待期，责任成立")
    void shouldWaiveWaitingPeriodForAccident() {
        RuleDecision decision = claimService.isClaimInCoverage(ClaimEnum.ClaimType.ACCIDENT,
                LocalDateTime.of(2026, 2, 1, 0, 0), coverageWith(90));

        assertEquals(DecisionType.APPROVED, decision.type());
    }

    @Test
    @DisplayName("等待期边界日（生效日+等待期当天）出险 → 已过等待期，责任成立")
    void shouldApproveOnWaitingPeriodBoundary() {
        RuleDecision decision = claimService.isClaimInCoverage(ClaimEnum.ClaimType.DEATH,
                LocalDateTime.of(2026, 4, 1, 0, 0), coverageWith(90));

        assertEquals(DecisionType.APPROVED, decision.type());
    }

    @Test
    @DisplayName("责任无等待期配置 → 直接责任成立")
    void shouldApproveWithoutWaitingPeriod() {
        RuleDecision decision = claimService.isClaimInCoverage(ClaimEnum.ClaimType.DEATH,
                LocalDateTime.of(2026, 1, 10, 0, 0), coverageWith(0));

        assertEquals(DecisionType.APPROVED, decision.type());
    }

    @Test
    @DisplayName("出险日期缺失但有责任 → 按等待期不可判不阻断，责任成立")
    void shouldApproveWhenIncidentDateMissing() {
        RuleDecision decision = claimService.isClaimInCoverage(ClaimEnum.ClaimType.DEATH,
                null, coverageWith(90));

        assertEquals(DecisionType.APPROVED, decision.type());
    }

    @Test
    @DisplayName("理赔类型缺失且无责任 → 责任除外（不空指针）")
    void shouldRejectWhenClaimTypeMissing() {
        CoverageResult result = new CoverageResult(EFFECTIVE, List.of());

        RuleDecision decision = claimService.isClaimInCoverage(null, null, result);

        assertEquals(DecisionType.REJECTED, decision.type());
    }
}
