package com.titanium.claim.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.titanium.claim.application.model.ClaimReadModel;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔查询结果 → 应用层读模型映射器（MapStruct）
 * <p>
 * 将 CQRS 读侧查询结果 {@link ClaimQueryResult} 声明式映射为应用层读模型 {@link ClaimReadModel}，
 * 取代应用服务中逐字段 set；状态/理赔类型枚举经空安全 {@code @Named} 方法收敛为 code 与中文描述。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimReadModelMapper {

    /**
     * 查询结果 → 应用层读模型（状态枚举拆为 code + 描述，理赔类型枚举收敛为 code）
     */
    @Mapping(target = "status", source = "status", qualifiedByName = "statusCode")
    @Mapping(target = "statusDescription", source = "status", qualifiedByName = "statusDescription")
    @Mapping(target = "claimType", source = "claimType", qualifiedByName = "claimTypeCode")
    ClaimReadModel toReadModel(ClaimQueryResult result);

    /**
     * 理赔状态枚举 → 状态码（空安全）
     */
    @Named("statusCode")
    default String statusCode(ClaimStatus status) {
        return status != null ? status.getCode() : null;
    }

    /**
     * 理赔状态枚举 → 中文描述（空安全）
     */
    @Named("statusDescription")
    default String statusDescription(ClaimStatus status) {
        return status != null ? status.getName() : null;
    }

    /**
     * 理赔类型枚举 → 类型码（空安全）
     */
    @Named("claimTypeCode")
    default String claimTypeCode(ClaimEnum.ClaimType claimType) {
        return claimType != null ? claimType.getCode() : null;
    }
}
