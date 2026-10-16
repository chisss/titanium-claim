package com.titanium.claim.application.orchestration.issuance.validator;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.common.exception.ClaimOutOfCoverageException;
import com.titanium.claim.port.clause.ClauseServicePort;
import com.titanium.claim.port.clause.ClauseServicePort.CoverageInfo;
import com.titanium.claim.port.policy.ClauseRef;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.claim.port.ruleengine.ClaimRuleExecutionPort;
import com.titanium.claim.port.ruleengine.ClaimRuleExecutionPort.DifferentiatedThresholdRequest;
import com.titanium.claim.service.ClaimService;
import com.titanium.claim.valueobject.ClaimDifferentiatedThreshold;
import com.titanium.claim.valueobject.CoverageResult;
import com.titanium.claim.valueobject.CoverageResult.CoverageMatch;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.valueobject.RuleDecision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 责任校验器（校验链第 3 环，跨微服务 Port 取数 + 领域服务纯规则判定）
 * <p>
 * 责任校验 CLAIM-4 的报案侧落地：<b>Port 取数（保单→条款→责任）→ 差异化阈值（规则引擎）→
 * 领域服务判定 → 结论分支</b>。
 * 取数编排（三次跨微服务调用）属 application 职责；「出险是否在责任范围内」的纯规则内聚于
 * {@link ClaimService#isClaimInCoverage}（§3.4.4 三无判据）。判定结论：
 * <ul>
 *   <li>责任成立 → 放行；</li>
 *   <li>需人工判定（等待期内非意外）→ 放行并告警日志，转人工核赔按条款处理（产品文档 §2.7）；</li>
 *   <li>责任除外 → 抛 {@link ClaimOutOfCoverageException} 中断报案。</li>
 * </ul>
 * </p>
 * <p>
 * 🔴 <b>差异化等待期（g09-03 · G09/AC-06）</b>：装配 {@link CoverageResult} 前经
 * {@link ClaimRuleExecutionPort} 求生效 CLAIM 规则集结论——有等待期结论 ⇒ 覆盖条款等待期
 * （产品级单一值，多责任同覆盖）；无结论 ⇒ 逐项回落条款值（既有路径逐字不变）。
 * 规则引擎不可达/应答无效由 Port 契约抛出（失败关闭），本类<b>不吞异常</b>。
 * </p>
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class CoverageValidator implements ClaimRegistrationValidator {

    private final PolicyServicePort        policyServicePort;
    private final ClauseServicePort        clauseServicePort;
    private final ClaimRuleExecutionPort   claimRuleExecutionPort;
    private final ClaimService             claimService;

    @Override
    public void validate(CreateClaimRequest request) {
        String tenantId = RequestContextHolder.getTenantId();
        // 1. Port 取数：保单（等待期起算基准）→ 保单条款（责任定位）→ 条款责任列表
        PolicyInfo policy = policyServicePort.getPolicy(request.getPolicyId(), tenantId);
        if (policy == null || policy.effectiveDate() == null) {
            log.warn("[报案校验] 保单或生效日期缺失，跳过责任校验, policyId={}", request.getPolicyId());
            return;
        }
        ClauseRef clause = pickClause(policyServicePort.fetchClauses(request.getPolicyId(), tenantId));
        if (clause == null) {
            log.warn("[报案校验] 保单无条款快照，无法自动定责，转人工核赔, policyId={}", request.getPolicyId());
            return;
        }
        List<CoverageInfo> coverages = clauseServicePort.fetchCoverages(clause.clauseId(), tenantId);

        // 2. 差异化阈值（g09-03）：查生效 CLAIM 规则集结论；无结论 ⇒ 逐项回落条款值。
        //    🔴 失败关闭：规则引擎不可达/应答无效由 Port 契约抛出，本处不吞异常、不静默回落。
        ClaimDifferentiatedThreshold threshold = claimRuleExecutionPort.resolveThreshold(
                new DifferentiatedThresholdRequest(tenantId, request.getPolicyId(), policy.productId(),
                        request.getClaimType()));
        Integer waitingPeriodOverride = threshold.waitingPeriodDays();
        if (waitingPeriodOverride != null) {
            log.info("[报案校验] 差异化等待期规则生效并覆盖条款值, policyId={}, productId={}, waitingPeriodDays={}",
                    request.getPolicyId(), policy.productId(), waitingPeriodOverride);
        }

        // 3. 翻译为领域值对象并交领域服务纯规则判定（覆盖值在装配时替换条款等待期）
        CoverageResult coverageResult = new CoverageResult(policy.effectiveDate(), coverages.stream()
                .map(coverage -> new CoverageMatch(coverage.coverageId(), coverage.coverageCode(),
                        coverage.coverageName(),
                        waitingPeriodOverride != null ? waitingPeriodOverride : coverage.waitingPeriodDays()))
                .toList());
        RuleDecision decision = claimService.isClaimInCoverage(
                ClaimEnum.ClaimType.fromCode(request.getClaimType()), request.getIncidentDate(), coverageResult);

        // 4. 结论分支：责任除外中断报案；需人工判定放行转人工核赔
        if (decision.isRejected()) {
            log.error("[报案校验] 责任判定拒绝, policyId={}, claimType={}", request.getPolicyId(),
                    request.getClaimType());
            throw new ClaimOutOfCoverageException();
        }
        if (decision.isManualReview()) {
            log.warn("[报案校验] 责任判定需人工（等待期内非意外出险）, policyId={}, claimType={}, incidentDate={}",
                    request.getPolicyId(), request.getClaimType(), request.getIncidentDate());
            return;
        }
        log.info("[报案校验] 责任判定通过, policyId={}, clauseId={}, coverageCount={}", request.getPolicyId(),
                clause.clauseId(), coverages.size());
    }

    /**
     * 责任校验的条款定位：主条款优先，无主条款标记取首条。
     */
    private ClauseRef pickClause(List<ClauseRef> clauses) {
        return clauses.stream()
                .filter(clause -> Boolean.TRUE.equals(clause.mainClause()))
                .findFirst()
                .orElse(clauses.isEmpty() ? null : clauses.get(0));
    }
}
