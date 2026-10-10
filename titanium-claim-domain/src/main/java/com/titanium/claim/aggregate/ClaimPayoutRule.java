package com.titanium.claim.aggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.exception.DomainException;

import lombok.Getter;

/**
 * 赔付规则聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 按「险种线 × 理赔类型」配置免赔额/赔付比例/单次限额/年度限额，宠物险按医院类型分档比例
 * （定点 70%/非定点 20~40% 可调）与责任免除清单。报销型理算公式
 * {@code min((合规费用 − 免赔额) × 赔付比例, 单次限额)} 的参数来源（产品文档 §2.7）。
 * 配置读取优先级：租户覆盖 &gt; 平台默认（product 模板兜底由调用方编排）。
 * </p>
 * <p>
 * 🔴 <b>量纲口径（G12/g12-05 定死，勿凭直觉改）</b>：本聚合全部比例字段
 * （{@code payoutRatio}、{@code hospitalTierRatios}、{@code socialSecurityRatios}）
 * 一律为 <b>0-100 百分比整数</b>（如 80 表示 80%；校验区间见构造器），换算落地<b>唯一位点</b>是
 * {@link com.titanium.claim.valueobject.ReimbursementCalculation#of}（÷100、HALF_UP 两位小数，
 * 由 {@code ReimbursementCalculationTest} 在行为面钉住）。与条款域
 * {@code clause.valueobject.Reimbursement#reimbursementRatio}（<b>0-1 小数</b>，如 0.9 表示 90%）
 * 口径不同：🔴 <b>禁止隐式换算</b>——跨域取用须新增显式转换器（÷100/×100 于转换器内完成并有单测），
 * 不得直接相乘、不得直接赋值、不得用同一字段承载两套口径。域内另有 0-1 小数口径的比例
 * （责任比例 {@code LossAssessment.liabilityRatio}、受益比例
 * {@code BenefitCalculation.BeneficiaryShare.benefitRatio}），不属报销链路，各自在值对象处声明。
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
    /** 社保身份分档赔付比例（人身险 G12/g12-03：key=社保身份码，value=0-100 百分比；空=未启用该维度） */
    private final Map<String, Integer> socialSecurityRatios;
    /** 责任免除清单（险种线内除外责任说明） */
    private final List<String>        exclusions;

    private ClaimPayoutRule(String ruleId, String tenantId, String insuranceLine, String claimType,
                            BigDecimal deductible, Integer payoutRatio, BigDecimal perClaimLimit,
                            BigDecimal annualLimit, Map<String, Integer> hospitalTierRatios,
                            Map<String, Integer> socialSecurityRatios, List<String> exclusions) {
        if (payoutRatio != null && (payoutRatio < 0 || payoutRatio > 100)) {
            throw new DomainException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "赔付比例必须在 0-100 之间");
        }
        if (deductible != null && perClaimLimit != null && deductible.compareTo(perClaimLimit) > 0) {
            throw new DomainException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "免赔额不能大于单次限额");
        }
        if (hospitalTierRatios != null) {
            hospitalTierRatios.values().forEach(ratio -> {
                if (ratio < 0 || ratio > 100) {
                    throw new DomainException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "医院分档比例必须在 0-100 之间");
                }
            });
        }
        if (socialSecurityRatios != null) {
            socialSecurityRatios.values().forEach(ratio -> {
                if (ratio < 0 || ratio > 100) {
                    throw new DomainException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "社保身份分档比例必须在 0-100 之间");
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
        this.socialSecurityRatios = socialSecurityRatios == null ? Map.of() : Map.copyOf(socialSecurityRatios);
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
     * @param socialSecurityRatios 社保身份分档比例（人身险 G12/g12-03，可为空=未启用该维度）
     * @param exclusions         责任免除清单（可为空）
     * @return 赔付规则聚合
     */
    public static ClaimPayoutRule create(String ruleId, String tenantId, String insuranceLine, String claimType,
                                         BigDecimal deductible, Integer payoutRatio, BigDecimal perClaimLimit,
                                         BigDecimal annualLimit, Map<String, Integer> hospitalTierRatios,
                                         Map<String, Integer> socialSecurityRatios, List<String> exclusions) {
        return new ClaimPayoutRule(ruleId, tenantId, insuranceLine, claimType, deductible, payoutRatio,
                perClaimLimit, annualLimit, hospitalTierRatios, socialSecurityRatios, exclusions);
    }

    /**
     * 全量更新赔付规则（后台表单全量提交，返回新实例）
     *
     * @return 更新后的赔付规则聚合
     */
    public ClaimPayoutRule update(String insuranceLine, String claimType, BigDecimal deductible,
                                  Integer payoutRatio, BigDecimal perClaimLimit, BigDecimal annualLimit,
                                  Map<String, Integer> hospitalTierRatios, Map<String, Integer> socialSecurityRatios,
                                  List<String> exclusions) {
        return new ClaimPayoutRule(ruleId, tenantId, insuranceLine, claimType, deductible, payoutRatio,
                perClaimLimit, annualLimit, hospitalTierRatios, socialSecurityRatios, exclusions);
    }

    /**
     * 是否配置了社保身份分档比例（人身险 G12/g12-03）。
     * <p>
     * 🔴 配置即启用社保维度唯一裁决：<b>未配置（空）时既有定点/非定点路径完全不变</b>（向后兼容）；
     * 配置后理算比例一律按社保身份取档。社保维度与定点分支的组合口径待 R-2 业务确认。
     * </p>
     *
     * @return true=已配置社保分档
     */
    public boolean hasSocialSecurityRatios() {
        return !socialSecurityRatios.isEmpty();
    }

    /**
     * 按社保身份取分档赔付比例（人身险 G12/g12-03）。
     * <p>
     * 🔴 身份码当前为开放 String（值域待 R-2 业务确认后由 metadata 枚举收口）；查不到该身份的档位返回
     * {@code null}，由调用方<b>显式拒绝</b>（fail-closed），<b>不得回落</b>到基础比例或定点/非定点档——
     * 「社保身份无档位」禁止静默降级为高档赔付（AC-03 反向判据）。
     * </p>
     *
     * @param socialSecurityIdentity 社保身份码（承保时点事实，须来自保单快照而非客户资料现值）
     * @return 该身份的赔付比例（0-100）；未配置该身份时返回 {@code null}
     */
    public Integer socialSecurityRatio(String socialSecurityIdentity) {
        return socialSecurityRatios.get(socialSecurityIdentity);
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

    /**
     * 是否配置了医院等级分档比例（人身险 G12/g12-04）。
     * <p>
     * 🔴 配置即启用等级选档：定点医院台账未配置赔付比例时，按医院等级查分档比例；
     * <b>未配置（空）时既有「台账比例 → 基础比例」链路完全不变</b>（向后兼容）。
     * </p>
     *
     * @return true=已配置等级分档
     */
    public boolean hasHospitalTierRatios() {
        return !hospitalTierRatios.isEmpty();
    }

    /**
     * 按医院等级取分档赔付比例（人身险 G12/g12-04、AC-05）。
     * <p>
     * 查不到该等级的档位返回 {@code null}，由调用方<b>显式拒绝</b>（fail-closed），
     * <b>不得静默回落</b> {@code payoutRatio}——AC-05 反向判据。「等级未配置」的最终处置
     * （显式拒绝 or 约定档）待 R-5 业务确认，当前与 AC-03 同法取显式拒绝。
     * </p>
     *
     * @param hospitalLevel 医院等级（如 一级/二级/三级/宠物专科）
     * @return 该等级的赔付比例（0-100）；未配置该等级时返回 {@code null}
     */
    public Integer hospitalTierRatio(String hospitalLevel) {
        return hospitalTierRatios.get(hospitalLevel);
    }
}
