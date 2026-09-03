package com.titanium.claim.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.infrastructure.entity.ClaimHospitalNetworkDO;
import com.titanium.claim.infrastructure.mapper.ClaimConfigPersistenceMapper;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimHospitalNetworkRepository;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 宠物医院网络仓储实现（domain 仓储接口的 JPA Adapter）
 * <p>
 * 状态存储聚合：聚合 → DO 经 MapStruct 转换；DO → 聚合经 {@code ClaimHospitalNetwork.create}
 * 工厂重建。医院台账无业务唯一键，按 hospitalId 主键 upsert；审计字段由仓储显式维护。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaClaimHospitalNetworkRepositoryAdapter implements ClaimHospitalNetworkRepository {

    private final JpaClaimHospitalNetworkRepository jpaRepository;
    private final ClaimConfigPersistenceMapper     mapper;

    @Override
    @Transactional
    public void save(ClaimHospitalNetwork hospital) {
        Optional<ClaimHospitalNetworkDO> existing = jpaRepository.findById(hospital.getHospitalId());
        ClaimHospitalNetworkDO fresh = mapper.toDO(hospital);
        if (existing.isPresent()) {
            fresh.setId(existing.get().getId());
            fresh.setCreateTime(existing.get().getCreateTime());
            fresh.setCreatedBy(existing.get().getCreatedBy());
        } else {
            fresh.setId(hospital.getHospitalId());
            fresh.setCreateTime(LocalDateTime.now());
            fresh.setCreatedBy(ClaimConstants.SYSTEM_OPERATOR);
        }
        fresh.setIsDeleted(0);
        fresh.setUpdateTime(LocalDateTime.now());
        fresh.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
        jpaRepository.save(fresh);
    }

    @Override
    public Optional<ClaimHospitalNetwork> findById(String tenantId, String hospitalId) {
        return jpaRepository.findByHospitalIdAndTenantIdAndIsDeleted(hospitalId, tenantId, 0)
                .map(this::toDomain);
    }

    @Override
    public List<ClaimHospitalNetwork> findByTenant(String tenantId) {
        return jpaRepository.findByTenantIdAndIsDeletedOrderByCreateTimeDesc(tenantId, 0).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ClaimHospitalNetwork> findByName(String tenantId, String hospitalName) {
        return jpaRepository.findByTenantIdAndHospitalNameAndIsDeleted(tenantId, hospitalName, 0)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String hospitalId) {
        jpaRepository.findByHospitalIdAndTenantIdAndIsDeleted(hospitalId, tenantId, 0).ifPresent(dataObject -> {
            dataObject.setIsDeleted(1);
            dataObject.setUpdateTime(LocalDateTime.now());
            dataObject.setUpdatedBy(ClaimConstants.SYSTEM_OPERATOR);
            jpaRepository.save(dataObject);
            log.info("逻辑删除医院台账, tenantId={}, hospitalId={}", tenantId, hospitalId);
        });
    }

    private ClaimHospitalNetwork toDomain(ClaimHospitalNetworkDO dataObject) {
        return ClaimHospitalNetwork.create(dataObject.getHospitalId(), dataObject.getTenantId(), dataObject.getHospitalName(),
                dataObject.getHospitalLevel(), HospitalAgreementStatus.fromCode(dataObject.getAgreementStatus()),
                dataObject.getPayoutRatio(), dataObject.getDirectSettlement(), dataObject.getAddress(), dataObject.getContactPhone());
    }
}
