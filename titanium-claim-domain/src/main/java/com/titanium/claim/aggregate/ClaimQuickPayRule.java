package com.titanium.claim.aggregate;

import java.math.BigDecimal;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 快赔规则聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 小额快赔通道自动核赔判据的配置来源（产品文档 §2.10）：按理赔类型配置金额阈值与开关，
 * 满足「理赔类型适用 + 金额 ≤ 阈值 + 无欺诈警示标记」的 PROCESSING 案件可自动理算核赔
 * （免人工审核，同时打快赔统计标记 QUICK_PAY）。租户覆盖 &gt; 平台默认（platform）。
 * </p>
 */
@Getter
public final class ClaimQuickPayRule {

    private final String     ruleId;
    private final String     tenantId;
    /** 适用理赔类型 code（metadata ClaimEnum.ClaimType，如 MEDICAL） */
    private final String     claimType;
    /** 通道开关（关闭时该类型案件不走快赔自动核赔） */
    private final boolean    enabled;
    /** 快赔金额阈值（元，案件理赔金额 ≤ 阈值方可自动核赔） */
    private final BigDecimal amountThreshold;

    private ClaimQuickPayRule(String ruleId, String tenantId, String claimType, boolean enabled,
                              BigDecimal amountThreshold) {
        if (claimType == null || claimType.isBlank()) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "快赔适用理赔类型不能为空");
        }
        if (amountThreshold == null || amountThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "快赔金额阈值必须大于 0");
        }
        this.ruleId = ruleId;
        this.tenantId = tenantId;
        this.claimType = claimType;
        this.enabled = enabled;
        this.amountThreshold = amountThreshold;
    }

    /**
     * 创建快赔规则
     *
     * @param ruleId          规则ID（application 雪花生成）
     * @param tenantId        租户ID（平台默认 'platform'）
     * @param claimType       适用理赔类型 code
     * @param enabled         通道开关
     * @param amountThreshold 快赔金额阈值（元，须大于 0）
     * @return 快赔规则聚合
     */
    public static ClaimQuickPayRule create(String ruleId, String tenantId, String claimType, boolean enabled,
                                           BigDecimal amountThreshold) {
        return new ClaimQuickPayRule(ruleId, tenantId, claimType, enabled, amountThreshold);
    }

    /**
     * 全量更新快赔规则（后台表单全量提交，返回新实例）
     *
     * @return 更新后的快赔规则聚合
     */
    public ClaimQuickPayRule update(String claimType, boolean enabled, BigDecimal amountThreshold) {
        return new ClaimQuickPayRule(ruleId, tenantId, claimType, enabled, amountThreshold);
    }

    /**
     * 快赔判据前半段（规则侧）：通道开启且案件理赔金额未超阈值。
     * 后半段（案件侧：PROCESSING 状态 + 无欺诈警示标记）由 {@code QuickPayOrchestrator} 判定。
     *
     * @param claimAmount 案件理赔金额
     * @return 规则侧判据命中返回 {@code true}
     */
    public boolean matches(BigDecimal claimAmount) {
        return enabled && claimAmount != null && claimAmount.compareTo(amountThreshold) <= 0;
    }
}
