package com.titanium.claim.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.titanium.claim.web.interceptor.TenantInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    public WebConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/** = Feign 契约入口（ClaimApiProvider）；/web/** = 后台/端上入口（Controller）。
        // 两套入口都须经 TenantInterceptor 写租户上下文，否则 TenantContext 恒为默认租户。
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**", "/web/**");
    }
}
