package com.titanium.claim.api;

import java.time.LocalDate;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.claim.api.response.fieldcatalog.ClaimFieldCatalogResponse;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.constraints.NotNull;

/**
 * Claim 字段目录跨域只读契约。
 *
 * <p>与 policy 侧同名契约同构，差异只有一处：**没有 {@code productType}/{@code policyType} 两个筛选维度**
 * —— 那是保单域专有的取数维度，客户域目录不按产品/险种切分。</p>
 *
 * <p>🔴 租户不进入参：由调用方经 {@code X-Tenant-Id} 请求头传递，服务端从请求上下文取
 * （目录本身是跨租户共享元数据，租户值只做回显，见 {@code G07/AC-10}）。</p>
 */
@FeignClient(
        name = "titanium-claim",
        contextId = "claimFieldCatalogApi",
        path = "/api/v1/claim-field-catalogs")
public interface ClaimFieldCatalogApi {

    /** 查询指定业务时点生效的权威字段目录。 */
    @GetMapping("/current")
    ApiResponse<ClaimFieldCatalogResponse> getCurrentCatalog(
            @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    @NotNull
                    LocalDate businessDate);
}
