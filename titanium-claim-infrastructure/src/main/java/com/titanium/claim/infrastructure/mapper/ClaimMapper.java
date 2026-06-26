package com.titanium.claim.infrastructure.mapper;

import com.titanium.claim.aggregate.Claim;
import com.titanium.claim.infrastructure.repository.entity.ClaimEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 理赔案件映射器
 * <p>
 * 用于在领域对象和基础设施对象之间进行转换
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimMapper {
    /**
     * 映射器实例
     */
    ClaimMapper INSTANCE = Mappers.getMapper(ClaimMapper.class);

    /**
     * 将领域对象转换为基础设施实体
     *
     * @param claim 领域对象
     * @param tenantId 租户ID
     * @return 基础设施实体
     */
    @Mapping(target = "claimId", expression = "java(claim.getClaimId().value())")
    @Mapping(target = "customerId", expression = "java(claim.getCustomerId().value())")
    @Mapping(target = "policyId", expression = "java(claim.getPolicyId().value())")
    @Mapping(target = "claimAmount", expression = "java(claim.getClaimAmount().value())")
    @Mapping(target = "tenantId", source = "tenantId")
    ClaimEntity toEntity(Claim claim, String tenantId);

    /**
     * 将基础设施实体转换为领域对象
     *
     * @param entity 基础设施实体
     * @return 领域对象
     */
    @Mapping(target = "claimId", expression = "java(com.titanium.claim.valueobject.ClaimId.of(entity.getClaimId()))")
    @Mapping(target = "customerId", expression = "java(com.titanium.claim.valueobject.CustomerId.of(entity.getCustomerId()))")
    @Mapping(target = "policyId", expression = "java(com.titanium.claim.valueobject.PolicyId.of(entity.getPolicyId()))")
    @Mapping(target = "claimAmount", expression = "java(com.titanium.claim.valueobject.ClaimAmount.of(entity.getClaimAmount()))")
    Claim toDomain(ClaimEntity entity);
}