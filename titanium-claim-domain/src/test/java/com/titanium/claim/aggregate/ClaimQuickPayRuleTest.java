package com.titanium.claim.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 快赔规则聚合根测试（dev-012c，产品文档 §2.10 小额快赔通道配置）
 * <p>
 * 验证 {@code ClaimQuickPayRule}：构造不变量（理赔类型非空、阈值 > 0）、
 * 全量更新返回新实例、快赔判据前半段 {@code matches}（通道开启 + 金额未超阈值）。
 * </p>
 */
@DisplayName("快赔规则")
class ClaimQuickPayRuleTest {

    private ClaimQuickPayRule rule() {
        return ClaimQuickPayRule.create("RULE-1", "T-1", "MEDICAL", true, new BigDecimal("5000"));
    }

    @Test
    @DisplayName("理赔类型为空 → 拒绝创建")
    void shouldRejectBlankClaimType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ClaimQuickPayRule.create("RULE-1", "T-1", " ", true, new BigDecimal("5000")));
        assertEquals(ClaimErrorCode.CLAIM_CONFIG_INVALID.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("阈值非正 → 拒绝创建")
    void shouldRejectNonPositiveThreshold() {
        assertThrows(BusinessException.class,
                () -> ClaimQuickPayRule.create("RULE-1", "T-1", "MEDICAL", true, BigDecimal.ZERO));
        assertThrows(BusinessException.class,
                () -> ClaimQuickPayRule.create("RULE-1", "T-1", "MEDICAL", true, null));
    }

    @Test
    @DisplayName("判据：通道开启且金额未超阈值 → 命中")
    void shouldMatchWhenEnabledAndWithinThreshold() {
        assertTrue(rule().matches(new BigDecimal("5000")));
        assertTrue(rule().matches(new BigDecimal("100")));
    }

    @Test
    @DisplayName("判据：金额超阈值 → 不命中")
    void shouldNotMatchWhenExceedingThreshold() {
        assertFalse(rule().matches(new BigDecimal("5000.01")));
    }

    @Test
    @DisplayName("判据：通道关闭 → 不命中（即使金额合规）")
    void shouldNotMatchWhenDisabled() {
        ClaimQuickPayRule disabled = ClaimQuickPayRule.create("RULE-2", "T-1", "MEDICAL", false,
                new BigDecimal("5000"));
        assertFalse(disabled.matches(new BigDecimal("100")));
    }

    @Test
    @DisplayName("全量更新：返回新实例且保留规则ID")
    void shouldUpdateImmutably() {
        ClaimQuickPayRule updated = rule().update("DISABILITY", false, new BigDecimal("8000"));
        assertEquals("RULE-1", updated.getRuleId());
        assertEquals("DISABILITY", updated.getClaimType());
        assertFalse(updated.isEnabled());
        assertEquals(0, updated.getAmountThreshold().compareTo(new BigDecimal("8000")));
        // 原实例不受影响（不可变）
        assertTrue(rule().isEnabled());
    }
}
