package com.titanium.claim.aggregate;

import java.util.List;
import java.util.Map;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 流程模板聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 按「险种线 × 案件类型」定义理赔流程模板：环节序列 + 各环节时限（小时）+ 责任角色 + 必经校验点，
 * 与产品 {@code ClaimConfig.claimStages} 对齐，运营可在管理后台调整。低频变更、高频读取的支撑配置，
 * 按根规约 §4.1 走状态存储 + JPA（不发事件、不投影），读侧直接查仓储。
 * </p>
 * <p>
 * 聚合不变量：环节序列非空；环节时限与必经校验点必须落在环节序列内。
 * </p>
 */
@Getter
public final class ClaimFlowTemplate {

    private final String              templateId;
    private final String              tenantId;
    /** 险种线（metadata InsuranceProductType code，如 MEDICAL/PET） */
    private final String              insuranceLine;
    /** 案件类型（metadata ClaimEnum.ClaimType code，如 MEDICAL_REIMBURSE/DEATH） */
    private final String              claimType;
    /** 环节序列（有序，如 报案→资料审核→核赔→给付） */
    private final List<String>        stageSequence;
    /** 各环节时限（小时），key 必须属于环节序列 */
    private final Map<String, Integer> stageTimeLimitHours;
    /** 责任角色（如 理赔专员/核赔人） */
    private final String              responsibleRole;
    /** 必经校验点（环节名列表，key 必须属于环节序列） */
    private final List<String>        mandatoryCheckpoints;

    private ClaimFlowTemplate(String templateId, String tenantId, String insuranceLine, String claimType,
                              List<String> stageSequence, Map<String, Integer> stageTimeLimitHours,
                              String responsibleRole, List<String> mandatoryCheckpoints) {
        if (stageSequence == null || stageSequence.isEmpty()) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "流程模板环节序列不能为空");
        }
        if (stageTimeLimitHours != null && !stageSequence.containsAll(stageTimeLimitHours.keySet())) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "环节时限必须对应环节序列内环节");
        }
        if (mandatoryCheckpoints != null && !stageSequence.containsAll(mandatoryCheckpoints)) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "必经校验点必须对应环节序列内环节");
        }
        this.templateId = templateId;
        this.tenantId = tenantId;
        this.insuranceLine = insuranceLine;
        this.claimType = claimType;
        this.stageSequence = stageSequence == null ? List.of() : List.copyOf(stageSequence);
        this.stageTimeLimitHours = stageTimeLimitHours == null ? Map.of() : Map.copyOf(stageTimeLimitHours);
        this.responsibleRole = responsibleRole;
        this.mandatoryCheckpoints = mandatoryCheckpoints == null ? List.of() : List.copyOf(mandatoryCheckpoints);
    }

    /**
     * 创建流程模板
     *
     * @param templateId          模板ID（application 雪花生成）
     * @param tenantId            租户ID（平台默认模板为 'platform'）
     * @param insuranceLine       险种线 code
     * @param claimType           案件类型 code
     * @param stageSequence       环节序列
     * @param stageTimeLimitHours 各环节时限（小时）
     * @param responsibleRole     责任角色
     * @param mandatoryCheckpoints 必经校验点
     * @return 流程模板聚合
     */
    public static ClaimFlowTemplate create(String templateId, String tenantId, String insuranceLine,
                                           String claimType, List<String> stageSequence,
                                           Map<String, Integer> stageTimeLimitHours,
                                           String responsibleRole, List<String> mandatoryCheckpoints) {
        return new ClaimFlowTemplate(templateId, tenantId, insuranceLine, claimType, stageSequence,
                stageTimeLimitHours, responsibleRole, mandatoryCheckpoints);
    }

    /**
     * 全量更新流程模板（后台表单全量提交，返回新实例）
     *
     * @return 更新后的流程模板聚合
     */
    public ClaimFlowTemplate update(String insuranceLine, String claimType, List<String> stageSequence,
                                    Map<String, Integer> stageTimeLimitHours,
                                    String responsibleRole, List<String> mandatoryCheckpoints) {
        return new ClaimFlowTemplate(templateId, tenantId, insuranceLine, claimType, stageSequence,
                stageTimeLimitHours, responsibleRole, mandatoryCheckpoints);
    }
}
