package com.titanium.claim.query.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.query.view.ClaimView;

/**
 * 理赔案件读模型仓储
 * <p>
 * CQRS 查询侧仓储，访问读模型表 {@code t_claim_view}。租户隔离经 {@code tenantId} 条件下推。
 * </p>
 */
@Repository
public interface ClaimViewRepository
        extends JpaRepository<ClaimView, String>, JpaSpecificationExecutor<ClaimView> {

    Optional<ClaimView> findByClaimId(String claimId);

    Optional<ClaimView> findByClaimIdAndTenantId(String claimId, String tenantId);

    List<ClaimView> findByCustomerId(String customerId);

    List<ClaimView> findByPolicyId(String policyId);

    List<ClaimView> findByStatus(ClaimStatus status);
}
