package com.titanium.claim.web.interceptor;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.titanium.common.context.RequestContextHolder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 租户拦截器：从请求头解析租户ID并写入 {@link RequestContextHolder}（缺头时兜底
 * {@code default-tenant}，与改造前 ThreadLocal 初值语义一致）。请求结束后只清租户维度，
 * 整包上下文由公共 {@code RequestContextInterceptor} 统一清理。
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String DEFAULT_TENANT_ID = "default-tenant";


    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = DEFAULT_TENANT_ID;
        }
        RequestContextHolder.setTenantId(tenantId.trim());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        RequestContextHolder.setTenantId(null);
    }
}
