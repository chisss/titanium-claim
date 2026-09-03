package com.titanium.claim.valueobject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.titanium.claim.common.enums.AlertType;

/**
 * 理赔警示标记值对象（反欺诈警示 + 统计口径标记）
 * <p>
 * 报案环节自动风险评分（延迟报案/多次报案）、规则引擎风险评分命中与快赔通道标记的统一载体。
 * 类型经 {@link AlertType} 枚举承载（落库 code，红线 20 禁止裸字符串描述），命中规则标识
 * {@code ruleCode} 引用 {@code ClaimConstants} 定义的稳定规则 key（如 30 天窗口规则），
 * 供审计追溯「哪个规则打上了这个标记」。
 * </p>
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}：历史事件序列化含派生属性 {@code fraudAlert}
 * （{@code isFraudAlert()} 被 Jackson 视为 getter），事件重放须忽略该未知字段，否则反序列化失败阻塞投影。
 * </p>
 *
 * @param type     警示标记类型（枚举）
 * @param ruleCode 命中规则标识（稳定规则 key，可空=人工标记）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertFlag(
        AlertType type,
        String ruleCode) {

    public AlertFlag {
        if (type == null) {
            throw new IllegalArgumentException("警示标记类型不能为空");
        }
    }

    /**
     * 是否为欺诈警示类标记（延迟报案/多次报案/风险评分）。
     * <p>
     * 快赔通道判据「无警示标记」仅排除欺诈警示类；快赔标记自身是统计口径标记，
     * 不阻断快赔（避免快赔案件标记后自我阻断）。
     * </p>
     *
     * @return 欺诈警示类返回 {@code true}
     */
    @JsonIgnore
    public boolean isFraudAlert() {
        return type == AlertType.LATE_REPORT || type == AlertType.MULTIPLE_REPORTS || type == AlertType.RISK_SCORE;
    }
}
