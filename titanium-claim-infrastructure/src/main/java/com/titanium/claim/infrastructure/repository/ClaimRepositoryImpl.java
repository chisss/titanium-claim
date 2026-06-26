package com.titanium.claim.infrastructure.repository;

import com.titanium.claim.aggregate.Claim;
import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.infrastructure.config.TenantContext;
import com.titanium.claim.infrastructure.mapper.ClaimMapper;
import com.titanium.claim.infrastructure.repository.entity.ClaimEntity;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimRepository;
import com.titanium.claim.repository.ClaimRepository;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@AllArgsConstructor
public class ClaimRepositoryImpl implements ClaimRepository {
    private final JpaClaimRepository jpaClaimRepository;
    private final TenantContext tenantContext; // 假设已经在项目中定义了TenantContext
    private final ClaimMapper claimMapper;

    @Override
    public Optional<Claim> findById(ClaimId claimId) {
        return jpaClaimRepository.findById(claimId.value())
                .map(claimMapper::toDomain);
    }

    @Override
    public List<Claim> findByCustomerId(CustomerId customerId) {
        return jpaClaimRepository.findByCustomerId(customerId.value())
                .stream()
                .map(claimMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Claim> findByPolicyId(PolicyId policyId) {
        return jpaClaimRepository.findByPolicyId(policyId.value())
                .stream()
                .map(claimMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Claim> findByStatus(ClaimStatus status) {
        return jpaClaimRepository.findByStatus(status)
                .stream()
                .map(claimMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Claim> findByClaimNumber(String claimNumber) {
        return jpaClaimRepository.findByClaimNumber(claimNumber)
                .map(claimMapper::toDomain);
    }

    @Override
    public List<Claim> findAll() {
        return jpaClaimRepository.findAll()
                .stream()
                .map(claimMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Claim save(Claim claim) {
        String tenantId = tenantContext.getCurrentTenantId();
        ClaimEntity entity = claimMapper.toEntity(claim, tenantId);
        ClaimEntity savedEntity = jpaClaimRepository.save(entity);
        return claimMapper.toDomain(savedEntity);
    }

    @Override
    public void deleteById(ClaimId claimId) {
        jpaClaimRepository.deleteById(claimId.value());
    }

    @Override
    public long countByCustomerId(CustomerId customerId) {
        return jpaClaimRepository.countByCustomerId(customerId.value());
    }

    @Override
    public long countByPolicyId(PolicyId policyId) {
        return jpaClaimRepository.countByPolicyId(policyId.value());
    }

    @Override
    public long countByStatus(ClaimStatus status) {
        return jpaClaimRepository.countByStatus(status);
    }
}