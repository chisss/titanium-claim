package com.titanium.claim.service.impl;

import org.springframework.stereotype.Service;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.common.enums.config.SettlementChannel;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.service.ReimbursementAdjustmentService;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;
import com.titanium.claim.valueobject.ReimbursementCalculation;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 报销理算领域服务实现（纯规则，无任何基础设施依赖，可脱离容器 new 直测）
 * <p>
 * 裁决规则（与产品文档 §2.7/§4.1 对齐）：
 * <ol>
 * <li>定点资格：出险医院在台账且协议状态 ACTIVE（{@link ClaimHospitalNetwork#isEligible()}）</li>
 * <li>定点比例：台账赔付比例优先，未配置回落规则基础比例；非定点比例：规则
 * {@code NON_DESIGNATED} 档位，缺省回落基础比例半数（{@link ClaimPayoutRule#nonDesignatedRatio()}）</li>
 * <li>金额理算：{@code min((合规费用 − 免赔额) × 比例, 单次限额)}，由
 * {@link ReimbursementCalculation} 值对象守护参数合法性</li>
 * </ol>
 * </p>
 */
@Service
public class ReimbursementAdjustmentServiceImpl implements ReimbursementAdjustmentService {

    @Override
    public ReimbursementAdjustmentResult adjust(ReimbursementAdjustmentRequest request) {
        ClaimPayoutRule rule = request.payoutRule();
        ClaimHospitalNetwork hospital = request.hospital();
        boolean designated = hospital != null && hospital.isEligible();

        Integer payoutRatio = designated
                ? designatedRatio(rule, hospital)
                : rule.nonDesignatedRatio();
        if (payoutRatio == null) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID,
                    "赔付规则未配置赔付比例，无法理算: " + rule.getClaimType());
        }

        ReimbursementCalculation calculation = ReimbursementCalculation.of(request.eligibleExpense(),
                rule.getDeductible(), payoutRatio, rule.getPerClaimLimit());
        SettlementChannel channel = designated ? SettlementChannel.DESIGNATED : SettlementChannel.NON_DESIGNATED;
        return new ReimbursementAdjustmentResult(calculation, payoutRatio, channel);
    }

    /**
     * 定点赔付比例：台账赔付比例优先，未配置回落规则基础比例。
     */
    private Integer designatedRatio(ClaimPayoutRule rule, ClaimHospitalNetwork hospital) {
        return hospital.getPayoutRatio() != null ? hospital.getPayoutRatio() : rule.getPayoutRatio();
    }
}
