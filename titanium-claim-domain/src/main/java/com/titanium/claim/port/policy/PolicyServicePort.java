package com.titanium.claim.port.policy;

import java.util.List;

/**
 * 保单服务出口 Port（对端域：policy）
 * <p>
 * 领域需要的保单能力契约：保单查询与状态校验（报案校验链）、保单条款定位与受益人主数据
 * （责任校验 CLAIM-4 与身故给付受益人核验）。
 * 实现为 infrastructure 层 {@code adapter/policy/PolicyServiceAdapter}（调 PolicyApi Feign）。
 * </p>
 */
public interface PolicyServicePort {

    /**
     * 查询保单信息（含状态），用于报案校验链「保单有效」校验与等待期起算。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单信息（不存在时返回 null，由调用方判定）
     */
    PolicyInfo getPolicy(String policyId, String tenantId);

    /**
     * 查询保单条款引用列表（保单签发时冻结的条款快照），责任校验的条款定位来源。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 条款引用列表（无条款时返回空列表）
     */
    List<ClauseRef> fetchClauses(String policyId, String tenantId);

    /**
     * 查询保单受益人主数据，身故给付受益人核验的比对基准（按顺位分配、拒绝未知受益人）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 受益人摘要列表（按受益顺位升序，无受益人时返回空列表）
     */
    List<BeneficiaryInfo> fetchBeneficiaries(String policyId, String tenantId);
}
