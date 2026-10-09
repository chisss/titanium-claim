package com.titanium.claim.infrastructure.adapter.ruleengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.claim.port.ruleengine.ClaimRuleExecutionPort;
import com.titanium.claim.port.ruleengine.ClaimRuleExecutionPort.DifferentiatedThresholdRequest;
import com.titanium.claim.valueobject.ClaimDifferentiatedThreshold;
import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.request.execution.RuleFacts;
import com.titanium.ruleengine.api.response.execution.RuleExecutionResultResponse;
import com.titanium.ruleengine.api.response.ruleset.RuleSetResponse;
import com.titanium.ruleengine.common.enums.RuleSetStatus;
import com.titanium.ruleengine.common.enums.RuleSetType;

/**
 * 差异化阈值规则执行适配器测试（g09-03 · AC-05 的规则引擎接线点）
 * <p>
 * 三态必须彼此可分，任一被压成同形即整条链静默失效：
 * <ul>
 *   <li><b>无生效规则集</b> ⇒ {@link ClaimDifferentiatedThreshold#EMPTY}（合法未配置态，消费端回落条款值）；</li>
 *   <li><b>有生效规则集</b> ⇒ 按 outputs 映射等待期与准入结论（缺键 = 该维度无结论）；</li>
 *   <li><b>问不到 / 应答无效</b>（不可达、非成功信封、载荷类型非法、等待期为负） ⇒ 抛
 *       {@link ClaimErrorCode#CLAIM_RULE_ENGINE_REMOTE_ERROR}，<b>禁</b>降级为 EMPTY
 *       ——「规则说无等待期」与「问不到规则」在消费端都是「不覆盖」，压成同形会让配置的阈值静默失效。</li>
 * </ul>
 * </p>
 * <p>
 * 🔴 <b>事实集是契约而非实现细节</b>：固定供给三键（端口常量现取，不手抄），值为 null 时仍须占位
 * （键缺失会让引用该变量的规则表达式在求值期显式失败，与「值就是空」是两种语义）。
 * </p>
 */
class ClaimRuleExecutionAdapterTest {

    private static final String POLICY_ID  = "POL-1";
    private static final String PRODUCT_ID = "P-ACC-01";
    private static final String CLAIM_TYPE = ClaimEnum.ClaimType.DEATH.getCode();

    private RuleEngineApi            api;
    private ClaimRuleExecutionAdapter adapter;
    private final ArgumentCaptor<RuleFacts> factsCaptor = ArgumentCaptor.forClass(RuleFacts.class);

    @BeforeEach
    void setUp() {
        api = mock(RuleEngineApi.class);
        adapter = new ClaimRuleExecutionAdapter(api);
    }

    @Test
    @DisplayName("无生效规则集（列表为空/仅草稿/编码或类型不符）→ 空结论，且不触发求值")
    void shouldReturnEmptyWhenNoActiveRuleSet() {
        when(api.listRuleSets(RuleSetType.CLAIM.getCode())).thenReturn(ApiResponse.success(List.of(
                ruleSet(ClaimRuleExecutionPort.RULE_SET_CODE, RuleSetType.CLAIM, RuleSetStatus.DRAFT),
                ruleSet("OTHER_CLAIM_RULE", RuleSetType.CLAIM, RuleSetStatus.ACTIVE),
                ruleSet(ClaimRuleExecutionPort.RULE_SET_CODE, RuleSetType.UNDERWRITING, RuleSetStatus.ACTIVE))));

        ClaimDifferentiatedThreshold threshold = adapter.resolveThreshold(request());

        assertTrue(threshold.isEmpty(), "草稿/异码/异类型都不构成生效规则集，须回落条款值");
        verify(api, never()).execute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("规则集列表为空 → 空结论（合法未配置态）")
    void shouldReturnEmptyWhenRuleSetListEmpty() {
        when(api.listRuleSets(RuleSetType.CLAIM.getCode())).thenReturn(ApiResponse.success(List.of()));

        assertTrue(adapter.resolveThreshold(request()).isEmpty());
    }

    @Test
    @DisplayName("生效规则集 → 求值并按 outputs 映射两维度结论")
    void shouldMapOutputsWhenActiveRuleSetExists() {
        activeRuleSet();
        when(api.execute(eq(ClaimRuleExecutionPort.RULE_SET_CODE), any(RuleFacts.class), any(), any()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder()
                        .outputs(Map.of(ClaimRuleExecutionPort.OUTPUT_WAITING_PERIOD_DAYS, 0,
                                ClaimRuleExecutionPort.OUTPUT_ADMISSION_DECISION, "APPROVED"))
                        .build()));

        ClaimDifferentiatedThreshold threshold = adapter.resolveThreshold(request());

        assertEquals(0, threshold.waitingPeriodDays().intValue());
        assertEquals("APPROVED", threshold.admissionDecision());
    }

    @Test
    @DisplayName("求值事实与业务头：三键固定供给，业务单号为保单号、业务域为理赔域")
    void shouldSendFactsAndBusinessHeaders() {
        activeRuleSet();
        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder()
                        .outputs(Map.of(ClaimRuleExecutionPort.OUTPUT_WAITING_PERIOD_DAYS, 90))
                        .build()));

        adapter.resolveThreshold(request());

        verify(api).execute(eq(ClaimRuleExecutionPort.RULE_SET_CODE), factsCaptor.capture(), eq(POLICY_ID),
                eq(BusinessDomainType.CLAIM.getCode()));
        Map<String, Object> facts = factsCaptor.getValue().values();
        assertEquals(Set.of(ClaimRuleExecutionPort.FACT_POLICY_ID, ClaimRuleExecutionPort.FACT_PRODUCT_ID,
                ClaimRuleExecutionPort.FACT_CLAIM_TYPE), facts.keySet(),
                "变量集固定三键：多供污染命名空间，少供会让规则表达式求值期显式失败");
        assertEquals(POLICY_ID, facts.get(ClaimRuleExecutionPort.FACT_POLICY_ID));
        assertEquals(PRODUCT_ID, facts.get(ClaimRuleExecutionPort.FACT_PRODUCT_ID));
        assertEquals(CLAIM_TYPE, facts.get(ClaimRuleExecutionPort.FACT_CLAIM_TYPE));
    }

    @Test
    @DisplayName("产品缺失：productId 为 null 仍须占位传递，不得整键丢弃")
    void shouldKeepNullValuedFactWhenProductAbsent() {
        activeRuleSet();
        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder()
                        .outputs(Map.of(ClaimRuleExecutionPort.OUTPUT_WAITING_PERIOD_DAYS, 90))
                        .build()));

        adapter.resolveThreshold(new DifferentiatedThresholdRequest("T-1", POLICY_ID, null, CLAIM_TYPE));

        verify(api).execute(any(), factsCaptor.capture(), any(), any());
        Map<String, Object> facts = factsCaptor.getValue().values();
        assertTrue(facts.containsKey(ClaimRuleExecutionPort.FACT_PRODUCT_ID),
                "键缺失会让引用该变量的规则表达式在求值期报错，与「值就是空」是两种语义");
        assertNull(facts.get(ClaimRuleExecutionPort.FACT_PRODUCT_ID));
    }

    @Test
    @DisplayName("部分输出：仅有等待期结论时准入维度为 null；outputs 缺失时两维度均 null")
    void shouldMapPartialOutputs() {
        activeRuleSet();
        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder()
                        .outputs(Map.of(ClaimRuleExecutionPort.OUTPUT_WAITING_PERIOD_DAYS, 90))
                        .build()));

        ClaimDifferentiatedThreshold partial = adapter.resolveThreshold(request());
        assertEquals(90, partial.waitingPeriodDays().intValue());
        assertNull(partial.admissionDecision(), "无准入结论 = 该维度回落，不得凭空造值");

        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder().build()));
        assertTrue(adapter.resolveThreshold(request()).isEmpty(), "无 outputs = 两维度均无结论");
    }

    @Test
    @DisplayName("查询规则集应答非成功 / 空应答 → 失败关闭，抛远程调用错误码")
    void shouldFailClosedWhenRuleSetQueryFails() {
        when(api.listRuleSets(RuleSetType.CLAIM.getCode()))
                .thenReturn(ApiResponse.error(SystemErrorCode.DEPENDENCY_SERVICE_ERROR))
                .thenReturn(null);

        assertEquals(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(DomainException.class, () -> adapter.resolveThreshold(request())).getErrorCodeValue());
        assertEquals(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(DomainException.class, () -> adapter.resolveThreshold(request())).getErrorCodeValue());
    }

    @Test
    @DisplayName("求值应答非成功 → 失败关闭（不得当作「无规则」回落条款值）")
    void shouldFailClosedWhenExecutionFails() {
        activeRuleSet();
        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(ApiResponse.error(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR));

        assertEquals(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(DomainException.class, () -> adapter.resolveThreshold(request())).getErrorCodeValue());
    }

    @Test
    @DisplayName("等待期输出非数值 → 失败关闭（不得静默当作 0 天用）")
    void shouldFailClosedWhenWaitingPeriodNotNumeric() {
        activeRuleSet();
        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(executionOf(ClaimRuleExecutionPort.OUTPUT_WAITING_PERIOD_DAYS, "90"));

        assertEquals(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(DomainException.class, () -> adapter.resolveThreshold(request())).getErrorCodeValue());
    }

    @Test
    @DisplayName("等待期输出为负 → 失败关闭")
    void shouldFailClosedWhenWaitingPeriodNegative() {
        activeRuleSet();
        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(executionOf(ClaimRuleExecutionPort.OUTPUT_WAITING_PERIOD_DAYS, -1));

        assertEquals(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(DomainException.class, () -> adapter.resolveThreshold(request())).getErrorCodeValue());
    }

    @Test
    @DisplayName("准入结论输出非字符串 → 失败关闭")
    void shouldFailClosedWhenAdmissionDecisionNotString() {
        activeRuleSet();
        when(api.execute(any(), any(RuleFacts.class), any(), any()))
                .thenReturn(executionOf(ClaimRuleExecutionPort.OUTPUT_ADMISSION_DECISION, 1));

        assertEquals(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR.getCode(),
                assertThrows(DomainException.class, () -> adapter.resolveThreshold(request())).getErrorCodeValue());
    }

    private void activeRuleSet() {
        when(api.listRuleSets(RuleSetType.CLAIM.getCode())).thenReturn(ApiResponse.success(List.of(
                ruleSet(ClaimRuleExecutionPort.RULE_SET_CODE, RuleSetType.CLAIM, RuleSetStatus.ACTIVE))));
    }

    private static ApiResponse<RuleExecutionResultResponse> executionOf(String key, Object value) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put(key, value);
        return ApiResponse.success(RuleExecutionResultResponse.builder().outputs(outputs).build());
    }

    private static RuleSetResponse ruleSet(String code, RuleSetType type, RuleSetStatus status) {
        RuleSetResponse ruleSet = new RuleSetResponse();
        ruleSet.setRuleSetCode(code);
        ruleSet.setRuleSetType(type);
        ruleSet.setStatus(status);
        return ruleSet;
    }

    private static DifferentiatedThresholdRequest request() {
        return new DifferentiatedThresholdRequest("T-1", POLICY_ID, PRODUCT_ID, CLAIM_TYPE);
    }
}
