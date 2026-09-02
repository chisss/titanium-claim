package com.titanium.claim.port.policy;

/**
 * 保单服务出口 Port（对端域：policy）
 * <p>
 * 领域需要的保单能力契约：保单查询与状态校验（报案校验链与身故给付用）。
 * 实现为 infrastructure 层 {@code adapter/policy/PolicyServiceAdapter}（调 PolicyApi Feign）。
 * </p>
 */
public interface PolicyServicePort {

    /**
     * 查询保单信息（含状态），用于报案校验链「保单有效」校验。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单信息（不存在时返回 null，由调用方判定）
     */
    PolicyInfo getPolicy(String policyId, String tenantId);
}
