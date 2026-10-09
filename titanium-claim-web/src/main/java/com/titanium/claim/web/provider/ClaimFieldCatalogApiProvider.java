package com.titanium.claim.web.provider;

import java.time.LocalDate;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.api.ClaimFieldCatalogApi;
import com.titanium.claim.api.response.fieldcatalog.ClaimFieldCatalogResponse;
import com.titanium.claim.application.query.ClaimFieldCatalogApplicationService;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalog;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogCriteria;
import com.titanium.claim.web.mapper.ClaimFieldCatalogWebMapper;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * Claim 字段目录跨域契约实现。
 *
 * <p>租户从请求上下文取（不进入参，调用方经 {@code X-Tenant-Id} 传递）。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/claim-field-catalogs")
@RequiredArgsConstructor
public class ClaimFieldCatalogApiProvider implements ClaimFieldCatalogApi {

    private final ClaimFieldCatalogApplicationService applicationService;
    private final ClaimFieldCatalogWebMapper mapper;

    @Override
    public ApiResponse<ClaimFieldCatalogResponse> getCurrentCatalog(LocalDate businessDate) {
        ClaimFieldCatalogCriteria criteria =
                new ClaimFieldCatalogCriteria(RequestContextHolder.requireTenantId(), businessDate);
        ClaimFieldCatalog catalog = applicationService.getCurrentCatalog(criteria);
        return ApiResponse.success(mapper.toResponse(criteria, catalog));
    }
}
