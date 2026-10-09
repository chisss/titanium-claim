package com.titanium.claim.web.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.titanium.claim.api.response.fieldcatalog.ClaimFieldCapabilityResponse;
import com.titanium.claim.api.response.fieldcatalog.ClaimFieldCatalogResponse;
import com.titanium.claim.api.response.fieldcatalog.ClaimFieldDescriptorResponse;
import com.titanium.claim.fieldcatalog.ClaimFieldCapability;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalog;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogCriteria;
import com.titanium.claim.fieldcatalog.ClaimFieldDescriptor;

/**
 * Claim 字段目录的边界映射器（领域对象 → 对外契约）。
 *
 * <p>字段级映射全部声明式交给 MapStruct 生成（同名同类型自动映射，能力对象递归映射），
 * 本接口只保留一个组合方法补齐**不由目录承载**的两个条件字段（租户、业务日期）。</p>
 */
@Mapper(componentModel = "spring")
public interface ClaimFieldCatalogWebMapper {

    /** 查询条件 + 目录 → 契约响应；条件字段在组合方法中回显。 */
    default ClaimFieldCatalogResponse toResponse(
            ClaimFieldCatalogCriteria criteria, ClaimFieldCatalog catalog) {
        return new ClaimFieldCatalogResponse(
                criteria.tenantId(),
                criteria.businessDate(),
                catalog.catalogVersion(),
                catalog.contentHash(),
                toDescriptorResponses(catalog.fields()));
    }

    /** 字段清单 → 响应清单。 */
    List<ClaimFieldDescriptorResponse> toDescriptorResponses(List<ClaimFieldDescriptor> descriptors);

    /** 字段描述 → 响应。 */
    ClaimFieldDescriptorResponse toResponse(ClaimFieldDescriptor descriptor);

    /** 字段能力 → 响应。 */
    ClaimFieldCapabilityResponse toResponse(ClaimFieldCapability capability);
}
