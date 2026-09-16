package com.titanium.claim.infrastructure.repository;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

/**
 * 理赔配置「业务键冲突」护栏
 * <p>
 * 理赔配置中心五组按业务键（租户 + 险种线/案件类型/环节等组合）唯一的配置，其仓储 {@code save()}
 * 需按业务键预查以规避唯一约束冲突。历史实现命中即<b>复用原主键与创建时间、覆盖内容</b>——
 * 于「编辑」语义正确，于「新建」却是<b>静默覆盖别人的既有规则</b>：行数不变、无覆盖提示、
 * 成功提示仍是「保存成功」，用户无从自证（D-501-53）。
 * </p>
 * <p>
 * 判据：业务键命中的行若<b>本就不是本次要写的那个聚合</b>（聚合ID 不同），说明调用方在做「新增」，
 * 而该业务键已被占用 —— 必须显式失败，交由调用方改走编辑入口或改选业务键。
 * 与「编辑」区分开：编辑传入的是已加载聚合的原ID，命中行的聚合ID 与之相同，正常放行。
 * </p>
 */
final class ClaimConfigBusinessKeyGuard {

    private ClaimConfigBusinessKeyGuard() {
    }

    /**
     * 业务键命中且非同一聚合时显式失败。
     *
     * @param configName         配置名称（用于错误消息，如「流程模板」）
     * @param businessKey        业务键描述（如 {@code MEDICAL/MEDICAL}）
     * @param existingAggregateId 库中已存在行的聚合ID
     * @param incomingAggregateId 本次写入的聚合ID
     * @throws BusinessException 业务键已被其它配置占用（{@link ClaimErrorCode#CLAIM_CONFIG_BUSINESS_KEY_EXISTS}）
     */
    static void rejectSilentOverwrite(String configName, String businessKey,
            String existingAggregateId, String incomingAggregateId) {
        if (existingAggregateId.equals(incomingAggregateId)) {
            return;
        }
        throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_BUSINESS_KEY_EXISTS,
                String.format("%s业务键[%s]已被配置[%s]占用，新增将覆盖既有规则；如需修改请改用编辑入口",
                        configName, businessKey, existingAggregateId));
    }
}
