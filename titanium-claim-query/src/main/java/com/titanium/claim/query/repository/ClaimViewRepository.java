package com.titanium.claim.query.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<ClaimView> findByCustomerIdAndTenantId(String customerId, String tenantId);

    List<ClaimView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    List<ClaimView> findByStatusAndTenantId(ClaimStatus status, String tenantId);

    List<ClaimView> findByTenantId(String tenantId);

    /**
     * 按租户ID统计理赔案件总数（多租户隔离）
     *
     * @param tenantId 租户ID
     * @return 理赔案件总数
     */
    long countByTenantId(String tenantId);

    /**
     * 按状态集合 + 租户ID统计理赔案件数（待处理数用未结案状态集合）
     *
     * @param statuses 状态集合
     * @param tenantId 租户ID
     * @return 命中状态的案件数
     */
    long countByStatusInAndTenantId(Collection<ClaimStatus> statuses, String tenantId);

    /**
     * 按创建时间区间 + 租户ID统计报案数（今日报案用当天 00:00~次日 00:00 半开区间）
     *
     * @param tenantId 租户ID
     * @param start 起始时间（含）
     * @param end 结束时间（不含）
     * @return 区间内报案数
     */
    long countByTenantIdAndCreateTimeGreaterThanEqualAndCreateTimeLessThan(String tenantId, LocalDateTime start,
            LocalDateTime end);

    /**
     * 累计已结案赔付金额（指定状态案件的核定赔付金额之和，多租户隔离）
     * <p>
     * 用 {@code COALESCE} 兜底空结果返回 0，避免无数据时返回 null。强制携带 {@code tenantId}。
     * </p>
     *
     * @param status 结案状态（传 {@code PAID}）
     * @param tenantId 租户ID
     * @return 已结案赔付金额之和，无数据为 0
     */
    @Query("SELECT COALESCE(SUM(c.settledAmount), 0) FROM ClaimView c "
            + "WHERE c.tenantId = :tenantId AND c.status = :status")
    BigDecimal sumSettledAmountByStatusAndTenantId(@Param("status") ClaimStatus status,
            @Param("tenantId") String tenantId);
}
