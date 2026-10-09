package com.titanium.claim.infrastructure.adapter.ruleengine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.claim.port.ruleengine.ClaimRuleExecutionPort;
import com.titanium.claim.valueobject.ClaimDifferentiatedThreshold;
import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.request.execution.RuleFacts;
import com.titanium.ruleengine.api.response.execution.RuleExecutionResultResponse;
import com.titanium.ruleengine.api.response.ruleset.RuleSetResponse;
import com.titanium.ruleengine.common.enums.RuleSetStatus;
import com.titanium.ruleengine.common.enums.RuleSetType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔差异化阈值规则执行适配器（对端域：rule-engine，g09-03 · G09/AC-05 · AC-06）。
 * <p>
 * 实现 {@link ClaimRuleExecutionPort}：直调对端 {@link RuleEngineApi} Feign（与 policy/clause/customer
 * 三域同构，不自建 Client）。三段式：
 * </p>
 * <ol>
 *   <li><b>查在册</b>：{@code listRuleSets("CLAIM")} 过滤「{@code CLAIM_DIFFERENTIATED_THRESHOLD}
 *       编码 + CLAIM 类型 + ACTIVE 状态」——无 ⇒ {@link ClaimDifferentiatedThreshold#EMPTY}
 *       （合法未配置态，消费端回落条款值）；</li>
 *   <li><b>求值</b>：{@code execute(ruleSetCode, facts, 业务头)}——事实三键见 Port 常量；</li>
 *   <li><b>映射结论</b>：从 {@code outputs} 取等待期（数值）与情景化准入（{@code RuleDecision}
 *       code 字符串）两键，缺键 = 该维度无结论、回落。</li>
 * </ol>
 * <p>
 * 🔴 <b>失败关闭（禁降级）</b>：查询/求值应答非成功、载荷无效（等待期非数值或为负）一律抛
 * {@link ClaimErrorCode#CLAIM_RULE_ENGINE_REMOTE_ERROR}——等待期触碰赔付结论，
 * 「规则说有」与「问不到规则」不得同形。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimRuleExecutionAdapter implements ClaimRuleExecutionPort {

    private final RuleEngineApi ruleEngineApi;

    @Override
    public ClaimDifferentiatedThreshold resolveThreshold(DifferentiatedThresholdRequest request) {
        // 1. 查在册：CLAIM 类型 + 固定编码 + ACTIVE（无 ⇒ 合法未配置态，回落条款值）
        ApiResponse<List<RuleSetResponse>> listResponse = ruleEngineApi.listRuleSets(RuleSetType.CLAIM.getCode());
        if (listResponse == null || !listResponse.isSuccess()) {
            throw remoteError("查询 CLAIM 规则集失败",
                    listResponse == null ? "空应答" : listResponse.getMessage());
        }
        boolean ruleSetActive = listResponse.getData() != null && listResponse.getData().stream()
                .anyMatch(ruleSet -> RULE_SET_CODE.equals(ruleSet.getRuleSetCode())
                        && RuleSetType.CLAIM == ruleSet.getRuleSetType()
                        && RuleSetStatus.ACTIVE == ruleSet.getStatus());
        if (!ruleSetActive) {
            log.info("无生效 CLAIM 差异化阈值规则集，回落条款值, ruleSetCode={}, policyId={}", RULE_SET_CODE,
                    request.policyId());
            return ClaimDifferentiatedThreshold.EMPTY;
        }

        // 2. 求值：事实三键（键名须为 SpEL 可寻址名，值可为 null——LinkedHashMap 允许 null 值）
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put(FACT_POLICY_ID, request.policyId());
        facts.put(FACT_PRODUCT_ID, request.productId());
        facts.put(FACT_CLAIM_TYPE, request.claimType());
        ApiResponse<RuleExecutionResultResponse> execResponse = ruleEngineApi.execute(RULE_SET_CODE,
                RuleFacts.of(facts), request.policyId(), BusinessDomainType.CLAIM.getCode());
        if (execResponse == null || !execResponse.isSuccess()) {
            throw remoteError("差异化阈值规则求值失败",
                    execResponse == null ? "空应答" : execResponse.getMessage());
        }

        // 3. 映射结论：缺键 = 该维度无规则输出、回落；类型非法 = 失败关闭
        Map<String, Object> outputs = execResponse.getData() == null ? null : execResponse.getData().getOutputs();
        Integer waitingPeriodDays = outputs == null ? null
                : toWaitingPeriodDays(outputs.get(OUTPUT_WAITING_PERIOD_DAYS));
        String admissionDecision = outputs == null ? null
                : toAdmissionDecision(outputs.get(OUTPUT_ADMISSION_DECISION));
        log.info("差异化阈值规则求值完成, policyId={}, waitingPeriodDays={}, admissionDecision={}",
                request.policyId(), waitingPeriodDays, admissionDecision);
        return new ClaimDifferentiatedThreshold(waitingPeriodDays, admissionDecision);
    }

    /** 等待期输出：数值且非负；缺键 null；其余（字符串/负数）一律失败关闭——配错了不得静默当 0 天用 */
    private static Integer toWaitingPeriodDays(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int days = number.intValue();
            if (days < 0) {
                throw remoteError("等待期输出为负数", String.valueOf(value));
            }
            return days;
        }
        throw remoteError("等待期输出非数值", String.valueOf(value));
    }

    /** 准入结论输出：非空字符串（RuleDecision 枚举 code）；缺键 null；其余失败关闭 */
    private static String toAdmissionDecision(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw remoteError("准入结论输出非字符串", String.valueOf(value));
    }

    private static DomainException remoteError(String action, String detail) {
        return new DomainException(ClaimErrorCode.CLAIM_RULE_ENGINE_REMOTE_ERROR, action + ": " + detail);
    }
}
