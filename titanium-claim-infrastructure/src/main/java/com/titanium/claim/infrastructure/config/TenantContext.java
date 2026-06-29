package com.titanium.claim.infrastructure.config;

import org.springframework.stereotype.Component;


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
