package com.titanium.claim.infrastructure.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.infrastructure.repository.entity.ClaimEntity;

@Repository
public interface JpaClaimRepository extends JpaRepository<ClaimEntity, String> {
    List<ClaimEntity> findByCustomerId(String customerId);
    List<ClaimEntity> findByPolicyId(String policyId);
    Optional<ClaimEntity> findByClaimNumber(String claimNumber);
    List<ClaimEntity> findByStatus(ClaimStatus status);
    long countByCustomerId(String customerId);
    long countByPolicyId(String policyId);
    long countByStatus(ClaimStatus status);
}
