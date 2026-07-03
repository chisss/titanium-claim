package com.titanium.claim.common.context;

import org.springframework.stereotype.Component;

/**
 * 租户上下文（多租户横切关注点，置于 claim-common 共享内核）
 * <p>
 * 以 ThreadLocal 保存当前请求的租户ID，供 Web 拦截器写入、基础设施仓储读取。 置于 common 层避免
 * web → infrastructure 的越层依赖（DDD 分层约束）。
 * </p>
 */
@Component
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = ThreadLocal.withInitial(() -> "default-tenant");

    public String getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }

    public void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public void clear() {
        CURRENT_TENANT.remove();
    }
}
