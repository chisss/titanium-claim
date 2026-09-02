package com.titanium.claim.infrastructure.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.claim.infrastructure.entity.ClaimHospitalNetworkDO;

/**
 * 宠物医院网络 Spring Data 仓储（基础设施层，框架代理）
 * <p>
 * 逻辑删除约定：查询一律过滤 {@code isDeleted=0}。
 * </p>
 */
public interface JpaClaimHospitalNetworkRepository extends JpaRepository<ClaimHospitalNetworkDO, String> {

    Optional<ClaimHospitalNetworkDO> findByHospitalIdAndTenantIdAndIsDeleted(String hospitalId, String tenantId,
                                                                             Integer isDeleted);

    List<ClaimHospitalNetworkDO> findByTenantIdAndIsDeletedOrderByCreateTimeDesc(String tenantId,
                                                                                 Integer isDeleted);

    Optional<ClaimHospitalNetworkDO> findByTenantIdAndHospitalNameAndIsDeleted(String tenantId, String hospitalName,
                                                                               Integer isDeleted);
}
