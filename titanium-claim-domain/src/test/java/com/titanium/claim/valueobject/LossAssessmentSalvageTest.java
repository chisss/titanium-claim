package com.titanium.claim.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 定损值对象残值扣减测试（车险定损明细扩展，dev-011）
 * <p>
 * 赔付金额 = (定损总金额 − 残值扣减) × 责任比例：验证残值参与赔付计算、
 * 残值超过定损金额时赔付归零、残值为空视为 0（向后兼容）。
 * </p>
 */
@DisplayName("定损残值扣减")
class LossAssessmentSalvageTest {

    private LossAssessment assessment(BigDecimal salvageValue) {
        return new LossAssessment(new BigDecimal("10000.00"),
                List.of(new LossAssessment.LossItem("保险杠", new BigDecimal("6000.00")),
                        new LossAssessment.LossItem("大灯", new BigDecimal("4000.00"))),
                salvageValue, new BigDecimal("0.8"), "assessor-1");
    }

    @Test
    @DisplayName("赔付金额扣除残值： (10000 − 1000) × 0.8 = 7200")
    void shouldDeductSalvageFromPayable() {
        assertEquals(0, assessment(new BigDecimal("1000.00")).payableAmount()
                .compareTo(new BigDecimal("7200.00")));
    }

    @Test
    @DisplayName("残值为空视为 0（向后兼容）")
    void shouldTreatNullSalvageAsZero() {
        assertEquals(0, assessment(null).payableAmount().compareTo(new BigDecimal("8000.00")));
    }

    @Test
    @DisplayName("残值超过定损金额 → 赔付归零")
    void shouldPayZeroWhenSalvageExceedsAssessment() {
        assertEquals(0, assessment(new BigDecimal("12000.00")).payableAmount().compareTo(BigDecimal.ZERO));
    }
}
