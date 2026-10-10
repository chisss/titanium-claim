package com.titanium.claim.service.impl;

import org.springframework.stereotype.Service;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.common.enums.config.SettlementChannel;
import com.titanium.claim.service.ReimbursementAdjustmentService;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest;
import com.titanium.claim.valueobject.ReimbursementAdjustmentRequest.ReimbursementAdjustmentResult;
import com.titanium.claim.valueobject.ReimbursementCalculation;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

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

        Integer payoutRatio = resolvePayoutRatio(rule, designated, hospital, request.socialSecurityIdentity());

        ReimbursementCalculation calculation = ReimbursementCalculation.of(request.eligibleExpense(),
                rule.getDeductible(), payoutRatio, rule.getPerClaimLimit());
        SettlementChannel channel = designated ? SettlementChannel.DESIGNATED : SettlementChannel.NON_DESIGNATED;
        return new ReimbursementAdjustmentResult(calculation, payoutRatio, channel);
    }

    /**
     * 赔付比例裁决（G12/g12-03 增社保身份维度）。
     * <p>
     * 🔴 配置了社保分档比例（{@link ClaimPayoutRule#hasSocialSecurityRatios()}）时，社保维度为<b>唯一裁决</b>：
     * 缺身份 / 身份无档均显式拒绝，不回落定点/非定点路径（AC-03 反向判据：缺失不得默认高档赔付）。
     * 未配置时既有定点/非定点裁决完全不变（向后兼容）。社保维度与定点分支的组合口径待 R-2 业务确认。
     * </p>
     */
    private Integer resolvePayoutRatio(ClaimPayoutRule rule, boolean designated, ClaimHospitalNetwork hospital,
                                       String socialSecurityIdentity) {
        if (rule.hasSocialSecurityRatios()) {
            return resolveSocialSecurityRatio(rule, socialSecurityIdentity);
        }
        Integer payoutRatio = designated ? designatedRatio(rule, hospital) : rule.nonDesignatedRatio();
        if (payoutRatio == null) {
            throw new DomainException(ClaimErrorCode.CLAIM_CONFIG_INVALID,
                    "赔付规则未配置赔付比例，无法理算: " + rule.getClaimType());
        }
        return payoutRatio;
    }

    /**
     * 社保身份分档比例裁决（AC-03）：缺身份显式拒绝（不得默认按「有社保」高档赔付），
     * 身份未配置档位同样显式拒绝（不得静默回落）。
     */
    private Integer resolveSocialSecurityRatio(ClaimPayoutRule rule, String socialSecurityIdentity) {
        if (socialSecurityIdentity == null || socialSecurityIdentity.isBlank()) {
            throw new DomainException(ClaimErrorCode.CLAIM_SOCIAL_SECURITY_REQUIRED,
                    rule.getClaimType() + " 已配置社保身份分档比例，理算未提供社保身份");
        }
        Integer ratio = rule.socialSecurityRatio(socialSecurityIdentity);
        if (ratio == null) {
            throw new DomainException(ClaimErrorCode.CLAIM_CONFIG_INVALID,
                    "社保身份未配置赔付比例: " + socialSecurityIdentity);
        }
        return ratio;
    }

    /**
     * 定点赔付比例（G12/g12-04 增等级选档）：台账赔付比例优先（个体配置）；
     * 台账未配时，规则配置了等级分档则按医院等级取档（缺档显式拒绝，AC-05 反向判据）；
     * 规则未配等级分档时回落基础比例（既有行为不变，向后兼容）。
     */
    private Integer designatedRatio(ClaimPayoutRule rule, ClaimHospitalNetwork hospital) {
        if (hospital.getPayoutRatio() != null) {
            return hospital.getPayoutRatio();
        }
        if (rule.hasHospitalTierRatios()) {
            return resolveHospitalTierRatio(rule, hospital);
        }
        return rule.getPayoutRatio();
    }

    /**
     * 医院等级分档比例裁决（AC-05）：规则配置了等级分档而该等级无档位时显式拒绝，
     * 不得静默回落 {@code payoutRatio}。「等级未配置」的最终处置（显式拒绝 or 约定档）
     * 待 R-5 业务确认，当前与 AC-03 同法取显式拒绝（fail-closed）。
     */
    private Integer resolveHospitalTierRatio(ClaimPayoutRule rule, ClaimHospitalNetwork hospital) {
        String level = hospital.getHospitalLevel();
        Integer ratio = rule.hospitalTierRatio(level);
        if (ratio == null) {
            throw new DomainException(ClaimErrorCode.CLAIM_CONFIG_INVALID,
                    "医院等级未配置赔付比例: " + (level == null || level.isBlank() ? "（出险医院未登记等级）" : level));
        }
        return ratio;
    }
}
