package com.titanium.claim.application.orchestration.issuance.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;
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
import com.titanium.claim.valueobject.RuleDecision;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 差异化等待期规则接线测试（g09-03 · G09/AC-05 · AC-06）
 * <p>
 * 验收面落在「规则结论是否真的改写了送达领域服务的等待期」：
 * <ul>
 *   <li><b>正（AC-06）</b>：同一索赔按产品配「意外险 0 天 / 重疾险 90 天」，两产品结论不同；</li>
 *   <li><b>反（AC-06）</b>：无生效规则集（{@link ClaimDifferentiatedThreshold#EMPTY}）⇒ 条款等待期
 *       原样进入领域调用，行为与接入前逐字一致（{@code ClaimService} 87-91 行路径不变）；</li>
 *   <li><b>失败关闭</b>：规则引擎不可达时 Port 抛出，本类透传、不吞异常、不静默回落。</li>
 * </ul>
 * </p>
 * <p>
 * 🔴 <b>为何用 spy + ArgumentCaptor 复算</b>：责任校验器对「责任成立」与「需人工判定」两种结论
 * <b>同为「不抛异常」</b>（只有责任除外才抛），从 {@code validate()} 的外部行为无法区分二者。
 * 故捕获送达 {@link ClaimService#isClaimInCoverage} 的 {@link CoverageResult} 实参，
 * 既断言规则覆盖值（0/90）确已进入领域调用，又用真实领域规则复算结论。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimWaitingPeriodRuleTest {

    /** 保单生效日（等待期起算基准） */
    private static final LocalDateTime POLICY_EFFECTIVE = LocalDateTime.of(2026, 5, 1, 0, 0);
    /** 出险日：生效后第 31 天——条款等待期 90 天时落在等待期内，规则覆盖为 0 天时已过等待期 */
    private static final LocalDateTime INCIDENT = LocalDateTime.of(2026, 6, 1, 0, 0);
    /** 条款等待期（既有链路取值，规则无结论时回落到它） */
    private static final int CLAUSE_WAITING_PERIOD_DAYS = 90;

    @Mock
    private PolicyServicePort policyServicePort;
    @Mock
    private ClauseServicePort clauseServicePort;
    @Mock
    private ClaimRuleExecutionPort claimRuleExecutionPort;
    @Spy
    private ClaimService claimService = new ClaimService();

    private CoverageValidator validator;

    @BeforeEach
    void setUp() {
        RequestContextHolder.setTenantId("T-1");
        // 默认「无生效规则集」：仅覆盖 Port 交互面，不约束本类关注的回落语义
        when(claimRuleExecutionPort.resolveThreshold(any()))
                .thenReturn(ClaimDifferentiatedThreshold.EMPTY);
        validator = new CoverageValidator(policyServicePort, clauseServicePort, claimRuleExecutionPort, claimService);
        when(policyServicePort.fetchClauses("POL-1", "T-1"))
                .thenReturn(List.of(new ClauseRef("CL-1", "CLAUSE-DEATH-01", "身故条款", true)));
        when(clauseServicePort.fetchCoverages("CL-1", "T-1"))
                .thenReturn(List.of(new CoverageInfo("COV-1", "COV-DEATH-01", "身故保险金",
                        CLAUSE_WAITING_PERIOD_DAYS)));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("AC-06 反：无生效规则集 → 条款等待期原样送达领域判定，结论与接入前一致（转人工）")
    void shouldFallBackToClauseWaitingPeriodWhenNoRuleConfigured() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(policy("P-CI-01"));

        assertDoesNotThrow(() -> validator.validate(request()));

        CoverageResult sent = capturedCoverage(1).get(0);
        assertEquals(CLAUSE_WAITING_PERIOD_DAYS, sent.matches().get(0).waitingPeriodDays().intValue(),
                "未配置规则时必须逐项回落条款等待期");
        assertTrue(recompute(sent).isManualReview(), "条款等待期 90 天 + 出险第 31 天 ⇒ 需人工判定");
    }

    @Test
    @DisplayName("AC-06 正：同一索赔按产品差异化——意外险 0 天责任成立，重疾险 90 天转人工")
    void shouldApplyDifferentiatedThresholdPerProduct() {
        when(claimRuleExecutionPort.resolveThreshold(any(DifferentiatedThresholdRequest.class)))
                .thenAnswer(invocation -> {
                    DifferentiatedThresholdRequest fact = invocation.getArgument(0);
                    return "P-ACC-01".equals(fact.productId())
                            ? new ClaimDifferentiatedThreshold(0, null)
                            : new ClaimDifferentiatedThreshold(CLAUSE_WAITING_PERIOD_DAYS, null);
                });

        // 同一索赔（同保单/同出险日/同理赔类型），仅产品不同——第一条按意外险产品出单
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(policy("P-ACC-01"));
        assertDoesNotThrow(() -> validator.validate(request()));
        // 第二条按重疾险产品出单
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(policy("P-CI-01"));
        assertDoesNotThrow(() -> validator.validate(request()));

        List<CoverageResult> sent = capturedCoverage(2);
        assertEquals(0, sent.get(0).matches().get(0).waitingPeriodDays().intValue(),
                "意外险产品：规则输出 0 天必须覆盖条款值 90 天");
        assertEquals(CLAUSE_WAITING_PERIOD_DAYS, sent.get(1).matches().get(0).waitingPeriodDays().intValue(),
                "重疾险产品：规则输出 90 天");
        assertTrue(recompute(sent.get(0)).isApproved(), "等待期 0 天 ⇒ 责任成立");
        assertTrue(recompute(sent.get(1)).isManualReview(), "等待期 90 天 ⇒ 需人工判定");
    }

    @Test
    @DisplayName("规则仅输出准入结论（无等待期结论）→ 等待期维度回落条款值")
    void shouldFallBackWaitingPeriodWhenRuleOutputsAdmissionOnly() {
        when(claimRuleExecutionPort.resolveThreshold(any()))
                .thenReturn(new ClaimDifferentiatedThreshold(null, "NEED_MANUAL"));
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(policy("P-CI-01"));

        assertDoesNotThrow(() -> validator.validate(request()));

        CoverageResult sent = capturedCoverage(1).get(0);
        assertEquals(CLAUSE_WAITING_PERIOD_DAYS, sent.matches().get(0).waitingPeriodDays().intValue(),
                "该维度无规则输出时回落条款值，不得被准入结论连带改写");
        assertTrue(recompute(sent).isManualReview());
    }

    @Test
    @DisplayName("失败关闭：规则引擎不可达 → 异常透传，不吞、不回落，且不发领域判定")
    void shouldPropagateWhenRuleEngineUnreachable() {
        when(claimRuleExecutionPort.resolveThreshold(any())).thenThrow(
                new DomainException(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR, "规则引擎不可达"));
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(policy("P-CI-01"));

        assertThrows(DomainException.class, () -> validator.validate(request()));

        verify(claimService, never()).isClaimInCoverage(any(), any(), any());
    }

    @Test
    @DisplayName("规则求值事实：租户/保单/产品/理赔类型四要素取自报案上下文与保单摘要")
    void shouldSendFactsFromRequestAndPolicy() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(policy("P-ACC-01"));

        assertDoesNotThrow(() -> validator.validate(request()));

        ArgumentCaptor<DifferentiatedThresholdRequest> captor =
                ArgumentCaptor.forClass(DifferentiatedThresholdRequest.class);
        verify(claimRuleExecutionPort).resolveThreshold(captor.capture());
        assertEquals("T-1", captor.getValue().tenantId());
        assertEquals("POL-1", captor.getValue().policyId());
        assertEquals("P-ACC-01", captor.getValue().productId());
        assertEquals(ClaimEnum.ClaimType.DEATH.getCode(), captor.getValue().claimType());
    }

    /** 捕获送达领域判定的责任匹配结果（按调用次序） */
    private List<CoverageResult> capturedCoverage(int expectedCalls) {
        ArgumentCaptor<CoverageResult> captor = ArgumentCaptor.forClass(CoverageResult.class);
        verify(claimService, times(expectedCalls)).isClaimInCoverage(any(), any(), captor.capture());
        return captor.getAllValues();
    }

    /** 以真实领域规则复算结论（校验器对该值不作区分，只能在此复算） */
    private RuleDecision recompute(CoverageResult sent) {
        return claimService.isClaimInCoverage(ClaimEnum.ClaimType.DEATH, INCIDENT, sent);
    }

    private CreateClaimRequest request() {
        CreateClaimRequest request = new CreateClaimRequest();
        request.setPolicyId("POL-1");
        request.setClaimType(ClaimEnum.ClaimType.DEATH.getCode());
        request.setIncidentDate(INCIDENT);
        request.setClaimAmount(new BigDecimal("10000"));
        return request;
    }

    private PolicyInfo policy(String productId) {
        return new PolicyInfo("POL-1", "EFFECTIVE", new BigDecimal("500000"), null, POLICY_EFFECTIVE, productId);
    }
}
