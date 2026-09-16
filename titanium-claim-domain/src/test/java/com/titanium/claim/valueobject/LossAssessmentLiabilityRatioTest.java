package com.titanium.claim.valueobject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.exception.ClaimLiabilityRatioException;

/**
 * 定损责任比例量纲测试（🔴 D-501-49）
 * <p>
 * 契约口径是 <b>0-1 小数</b>（全责 1.0、同责 0.5），人机终端习惯按 <b>百分数</b> 录入（80 = 80%）。
 * 二者拼在一起差 100 倍，而该比例直接决定核定赔付金额 —— 后者是核赔结算的唯一权威，会真实出账。
 * 故断言：越界（典型为百分数量纲）在<b>构造处即拒</b>，合法值不被误伤。
 * </p>
 * <p>
 * ⚠️ 本类只锁「后端值对象是否拒绝百分数量纲」。原缺陷的现场是<b>跨层</b>的
 * （页面百分数 → 请求体小数 → 落库金额），单测结构上不可能覆盖该路径 ——
 * 该路径由前端契约测试 {@code tests/claim-ratio-contracts.test.mjs} 锁定转换点。
 * </p>
 */
@DisplayName("定损责任比例量纲")
class LossAssessmentLiabilityRatioTest {

    private static final BigDecimal ASSESSED_AMOUNT = new BigDecimal("10000.00");

    private LossAssessment assessment(BigDecimal liabilityRatio) {
        return new LossAssessment(ASSESSED_AMOUNT,
                List.of(new LossAssessment.LossItem("保险杠", new BigDecimal("6000.00"))),
                BigDecimal.ZERO, liabilityRatio, "assessor-1");
    }

    @Test
    @DisplayName("百分数量纲（页面按 80 提交）在构造处即拒，不得静默放大 100 倍")
    void shouldRejectPercentScale() {
        ClaimLiabilityRatioException exception = assertThrows(ClaimLiabilityRatioException.class,
                () -> assessment(new BigDecimal("80")));

        assertEquals("30001021", exception.getErrorCode(), "须携带赔付比例无效的错误码");
    }

    @Test
    @DisplayName("越界值上界同样拒绝：1.5 与 101 均非法")
    void shouldRejectValueAboveOne() {
        assertThrows(ClaimLiabilityRatioException.class, () -> assessment(new BigDecimal("1.5")));
        assertThrows(ClaimLiabilityRatioException.class, () -> assessment(new BigDecimal("101")));
    }

    @Test
    @DisplayName("负比例拒绝：责任比例不得为负")
    void shouldRejectNegativeRatio() {
        assertThrows(ClaimLiabilityRatioException.class, () -> assessment(new BigDecimal("-0.1")));
    }

    @Test
    @DisplayName("合法小数区间不被误伤：0 / 0.5 / 0.8 / 1 全部放行")
    void shouldAcceptDecimalScale() {
        for (String ratio : List.of("0", "0.5", "0.8", "1")) {
            assertDoesNotThrow(() -> assessment(new BigDecimal(ratio)), "比例 " + ratio + " 属合法小数区间");
        }
    }

    @Test
    @DisplayName("边界值 1 表示全责：10000 × 1.0 = 10000（合法满额赔付）")
    void shouldPayFullAmountOnFullLiability() {
        assertEquals(0, assessment(BigDecimal.ONE).payableAmount().compareTo(ASSESSED_AMOUNT));
    }

    @Test
    @DisplayName("比例为空保持原容忍语义（不参与计算，由结算金额校验兜底）")
    void shouldTolerateNullRatio() {
        assertDoesNotThrow(() -> assessment(null));
    }
}
