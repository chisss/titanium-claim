package com.titanium.claim.service;

import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;

/**
 * 报销理算领域服务（跨聚合纯规则：赔付规则 × 医院网络 × 费用）
 * <p>
 * 健康险/宠物险报销型理赔的定点/非定点比例裁决与金额理算，属「不属于任何单个聚合根的纯业务计算」
 * （规则参数来自 {@code ClaimPayoutRule} 聚合、定点资格来自 {@code ClaimHospitalNetwork} 聚合），
 * 满足 §3.4.4 三无判据（无 CommandGateway、无 Port、无基础设施依赖），可脱离容器直测。
 * 取数（查规则/查医院台账）由 application 编排，本服务只做决策与计算。
 * </p>
 */
public interface ReimbursementAdjustmentService {

    /**
     * 报销理算：定点资格裁决 → 赔付比例选择 → 公式计算。
     * <ul>
     * <li>定点（台账 ACTIVE）：比例 = 台账赔付比例（未配置回落规则基础比例）</li>
     * <li>非定点：比例 = 规则非定点档位（{@code NON_DESIGNATED}，缺省回落基础比例半数）</li>
     * </ul>
     *
     * @param request 理算入参（合规费用 + 赔付规则 + 出险医院，医院空=非定点）
     * @return 理算结果（应付金额/实际比例/结算渠道）
     */
    ReimbursementAdjustmentResult adjust(ReimbursementAdjustmentRequest request);
}
