package com.titanium.claim.aggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 赔付规则聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 按「险种线 × 理赔类型」配置免赔额/赔付比例/单次限额/年度限额，宠物险按医院类型分档比例
 * （定点 70%/非定点 20~40% 可调）与责任免除清单。报销型理算公式
 * {@code min((合规费用 − 免赔额) × 赔付比例, 单次限额)} 的参数来源（产品文档 §2.7）。
 * 配置读取优先级：租户覆盖 &gt; 平台默认（product 模板兜底由调用方编排）。
 * </p>
 */
@Getter
public final class ClaimPayoutRule {

    /** 医院分档键：非定点档位（宠物险出险医院不在直赔网络台账时套用的赔付比例档位） */
    public static final String NON_DESIGNATED_TIER = "NON_DESIGNATED";

    private final String              ruleId;
    private final String              tenantId;
    /** 险种线（metadata InsuranceProductType code） */
    private final String              insuranceLine;
    /** 理赔类型（metadata ClaimEnum.ClaimType code） */
    private final String              claimType;
    /** 免赔额（元） */
    private final BigDecimal          deductible;
    /** 赔付比例（0-100 百分比） */
    private final Integer             payoutRatio;
    /** 单次限额（元，可为空表示不限） */
    private final BigDecimal          perClaimLimit;
    /** 年度限额（元，可为空表示不限） */
    private final BigDecimal          annualLimit;
    /** 医院分档赔付比例（宠物险：key=医院等级/定点标志，value=0-100 百分比） */
    private final Map<String, Integer> hospitalTierRatios;
    /** 责任免除清单（险种线内除外责任说明） */
    private final List<String>        exclusions;

    private ClaimPayoutRule(String ruleId, String tenantId, String insuranceLine, String claimType,
                            BigDecimal deductible, Integer payoutRatio, BigDecimal perClaimLimit,
                            BigDecimal annualLimit, Map<String, Integer> hospitalTierRatios,
                            List<String> exclusions) {
        if (payoutRatio != null && (payoutRatio < 0 || payoutRatio > 100)) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "赔付比例必须在 0-100 之间");
        }
        if (deductible != null && perClaimLimit != null && deductible.compareTo(perClaimLimit) > 0) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "免赔额不能大于单次限额");
        }
        if (hospitalTierRatios != null) {
            hospitalTierRatios.values().forEach(ratio -> {
                if (ratio < 0 || ratio > 100) {
                    throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "医院分档比例必须在 0-100 之间");
                }
            });
        }
        this.ruleId = ruleId;
        this.tenantId = tenantId;
        this.insuranceLine = insuranceLine;
        this.claimType = claimType;
        this.deductible = deductible;
        this.payoutRatio = payoutRatio;
        this.perClaimLimit = perClaimLimit;
        this.annualLimit = annualLimit;
        this.hospitalTierRatios = hospitalTierRatios == null ? Map.of() : Map.copyOf(hospitalTierRatios);
        this.exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    /**
     * 创建赔付规则
     *
     * @param ruleId             规则ID（application 雪花生成）
     * @param tenantId           租户ID（平台默认 'platform'）
     * @param insuranceLine      险种线 code
     * @param claimType          理赔类型 code
     * @param deductible         免赔额（元，可为空）
     * @param payoutRatio        赔付比例（0-100）
     * @param perClaimLimit      单次限额（元，可为空）
     * @param annualLimit        年度限额（元，可为空）
     * @param hospitalTierRatios 医院分档比例（可为空）
     * @param exclusions         责任免除清单（可为空）
     * @return 赔付规则聚合
     */
    public static ClaimPayoutRule create(String ruleId, String tenantId, String insuranceLine, String claimType,
                                         BigDecimal deductible, Integer payoutRatio, BigDecimal perClaimLimit,
                                         BigDecimal annualLimit, Map<String, Integer> hospitalTierRatios,
                                         List<String> exclusions) {
        return new ClaimPayoutRule(ruleId, tenantId, insuranceLine, claimType, deductible, payoutRatio,
                perClaimLimit, annualLimit, hospitalTierRatios, exclusions);
    }

    /**
     * 全量更新赔付规则（后台表单全量提交，返回新实例）
     *
     * @return 更新后的赔付规则聚合
     */
    public ClaimPayoutRule update(String insuranceLine, String claimType, BigDecimal deductible,
                                  Integer payoutRatio, BigDecimal perClaimLimit, BigDecimal annualLimit,
                                  Map<String, Integer> hospitalTierRatios, List<String> exclusions) {
        return new ClaimPayoutRule(ruleId, tenantId, insuranceLine, claimType, deductible, payoutRatio,
                perClaimLimit, annualLimit, hospitalTierRatios, exclusions);
    }

    /**
     * 非定点医院赔付比例：优先取医院分档配置 {@code NON_DESIGNATED} 档位；
     * 未配置时按保险业通行惯例回落为基础赔付比例的半数（HALF_UP 取整）。
     *
     * @return 非定点赔付比例（0-100）；基础比例未配置时返回 {@code null}
     */
    public Integer nonDesignatedRatio() {
        Integer tierRatio = hospitalTierRatios.get(NON_DESIGNATED_TIER);
        if (tierRatio != null) {
            return tierRatio;
        }
        if (payoutRatio == null) {
            return null;
        }
        return new BigDecimal(payoutRatio).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP).intValue();
    }
}
