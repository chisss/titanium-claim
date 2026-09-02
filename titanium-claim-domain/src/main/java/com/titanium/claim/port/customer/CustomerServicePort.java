package com.titanium.claim.port.customer;

/**
 * 客户服务出口 Port（对端域：customer）
 * <p>
 * 领域需要的客户能力契约：客户/受益人身份核验（CLAIM-4 受益人顺位核验）。
 * 实现为 infrastructure 层 {@code adapter/customer/CustomerServiceAdapter}（M2 接 customer 域 Feign）。
 * </p>
 */
public interface CustomerServicePort {

    /**
     * 查询客户信息（含身份状态），供受益人身份核验。
     *
     * @param customerId 客户ID
     * @param tenantId   租户ID
     * @return 客户信息（不存在时返回 null，由调用方判定）
     */
    CustomerInfo getCustomer(String customerId, String tenantId);

    /**
     * 客户信息（CustomerServicePort 出参，领域视角的客户摘要）
     *
     * @param customerId 客户ID
     * @param name       客户姓名
     * @param statusCode 客户状态码（对端域原生状态码）
     */
    record CustomerInfo(
            String customerId,
            String name,
            String statusCode
    ) {
    }
}
