package com.titanium.claim.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.common.enums.AlertType;

/**
 * 理赔警示标记值对象测试（dev-012b）
 * <p>
 * 验证 {@code AlertFlag}：类型非空不变量、欺诈警示类判定（延迟报案/多次报案/风险评分阻断快赔，
 * 快赔标记自身是统计口径标记不阻断）；{@code AlertType.fromCode} 反查。
 * </p>
 */
@DisplayName("理赔警示标记")
class AlertFlagTest {

    @Test
    @DisplayName("类型为空 → 拒绝构造")
    void shouldRejectNullType() {
        assertThrows(IllegalArgumentException.class, () -> new AlertFlag(null, "RULE"));
    }

    @Test
    @DisplayName("欺诈警示类判定：延迟报案/多次报案/风险评分阻断快赔")
    void shouldClassifyFraudAlerts() {
        assertTrue(new AlertFlag(AlertType.LATE_REPORT, "R1").isFraudAlert());
        assertTrue(new AlertFlag(AlertType.MULTIPLE_REPORTS, "R2").isFraudAlert());
        assertTrue(new AlertFlag(AlertType.RISK_SCORE, "R3").isFraudAlert());
    }

    @Test
    @DisplayName("快赔标记是统计口径标记：不阻断快赔（避免自我阻断）")
    void shouldNotClassifyQuickPayAsFraud() {
        assertFalse(new AlertFlag(AlertType.QUICK_PAY, "R4").isFraudAlert());
    }

    @Test
    @DisplayName("AlertType code 反查")
    void shouldResolveAlertTypeFromCode() {
        assertEquals(AlertType.LATE_REPORT, AlertType.fromCode("LATE_REPORT"));
        assertEquals(AlertType.QUICK_PAY, AlertType.fromCode("QUICK_PAY"));
    }
}
