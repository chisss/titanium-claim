package com.titanium.claim.aggregate;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 时限规则聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 按「险种线 × 案件环节」配置各环节处理时限与预警时限（小时），默认值对齐监管硬时限
 * （车险 48h 报案警示/3 日立案/3·10·30 日核定/3 日拒赔通知/10 日支付），允许租户在
 * 合规范围内收紧（产品文档 §2.9）。时效督办预警的数据源。
 * </p>
 */
@Getter
public final class ClaimTimeLimitRule {

    private final String  ruleId;
    private final String  tenantId;
    /** 险种线（metadata InsuranceProductType code） */
    private final String  insuranceLine;
    /** 案件环节 code（对齐流程模板 stageSequence 元素） */
    private final String  claimStage;
    /** 环节处理时限（小时，>0） */
    private final Integer limitHours;
    /** 预警时限（小时，0≤alertHours<limitHours，为 0 表示不预警） */
    private final Integer alertHours;

    private ClaimTimeLimitRule(String ruleId, String tenantId, String insuranceLine, String claimStage,
                               Integer limitHours, Integer alertHours) {
        if (limitHours == null || limitHours <= 0) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "环节时限必须大于 0 小时");
        }
        if (alertHours != null && (alertHours < 0 || alertHours >= limitHours)) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "预警时限必须小于环节时限且不小于 0");
        }
        this.ruleId = ruleId;
        this.tenantId = tenantId;
        this.insuranceLine = insuranceLine;
        this.claimStage = claimStage;
        this.limitHours = limitHours;
        this.alertHours = alertHours;
    }

    /**
     * 创建时限规则
     *
     * @param ruleId        规则ID（application 雪花生成）
     * @param tenantId      租户ID（平台默认 'platform'）
     * @param insuranceLine 险种线 code
     * @param claimStage    案件环节 code
     * @param limitHours    环节处理时限（小时）
     * @param alertHours    预警时限（小时，0 表示不预警）
     * @return 时限规则聚合
     */
    public static ClaimTimeLimitRule create(String ruleId, String tenantId, String insuranceLine,
                                            String claimStage, Integer limitHours, Integer alertHours) {
        return new ClaimTimeLimitRule(ruleId, tenantId, insuranceLine, claimStage, limitHours, alertHours);
    }

    /**
     * 全量更新时限规则（后台表单全量提交，返回新实例）
     *
     * @return 更新后的时限规则聚合
     */
    public ClaimTimeLimitRule update(String insuranceLine, String claimStage,
                                     Integer limitHours, Integer alertHours) {
        return new ClaimTimeLimitRule(ruleId, tenantId, insuranceLine, claimStage, limitHours, alertHours);
    }
}
