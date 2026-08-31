package com.titanium.claim.query.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.query.mapper.ClaimQueryResultMapper;
import com.titanium.claim.query.query.SearchClaimSummariesQuery;
import com.titanium.claim.query.repository.ClaimViewRepository;
import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.claim.query.result.ClaimStatisticsResult;
import com.titanium.claim.query.service.ClaimQueryService;
import com.titanium.claim.query.view.ClaimView;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔查询服务实现（CQRS 读侧）
 * <p>
 * 查询读模型表 {@code t_claim_view}（由 {@code ClaimProjectionEventHandler} 投影维护），
 * 经 {@link ClaimQueryResultMapper} 声明式组装为稳定 DTO 返回，禁止直接返回读模型实体。
 * 复杂查询（多条件搜索）以 JPA Specification 动态组装内聚于此，不在 application/Controller 编排。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimQueryServiceImpl implements ClaimQueryService {

    private final ClaimViewRepository      claimViewRepository;
    private final ClaimQueryResultMapper   claimQueryResultMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<ClaimQueryResult> getClaimSummary(String claimId, String tenantId) {
        return claimViewRepository.findByClaimIdAndTenantId(claimId, tenantId)
                .map(claimQueryResultMapper::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getClaimSummariesByCustomerId(String customerId, String tenantId) {
        return claimViewRepository.findByCustomerIdAndTenantId(customerId, tenantId).stream()
                .map(claimQueryResultMapper::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getClaimSummariesByPolicyId(String policyId, String tenantId) {
        return claimViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .map(claimQueryResultMapper::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getClaimSummariesByStatus(String status, String tenantId) {
        return claimViewRepository.findByStatusAndTenantId(ClaimStatus.fromCode(status), tenantId).stream()
                .map(claimQueryResultMapper::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> getAllClaimSummaries(String tenantId) {
        return claimViewRepository.findByTenantId(tenantId).stream()
                .map(claimQueryResultMapper::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimQueryResult> searchClaimSummaries(SearchClaimSummariesQuery query, int page, int size,
            String tenantId) {
        // 状态码非法：与历史内存过滤语义一致，返回空结果而非抛业务异常
        ClaimStatus statusEnum = resolveStatus(query.status());
        if (query.status() != null && statusEnum == null) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return claimViewRepository.findAll(buildSearchSpec(query, statusEnum, tenantId), pageable)
                .getContent().stream()
                .map(claimQueryResultMapper::toResult)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ClaimStatisticsResult getStatistics(String tenantId) {
        // 待处理数：未结案状态集合（PENDING/PROCESSING/APPROVED，排除终态 PAID/REJECTED）
        List<ClaimStatus> pendingStatuses = List.of(ClaimStatus.PENDING, ClaimStatus.PROCESSING, ClaimStatus.APPROVED);
        long pendingCount = claimViewRepository.countByStatusInAndTenantId(pendingStatuses, tenantId);
        // 今日报案数：create_time 落在 [今日 00:00, 次日 00:00) 半开区间
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        long todayCount = claimViewRepository
                .countByTenantIdAndCreateTimeGreaterThanEqualAndCreateTimeLessThan(tenantId, todayStart, tomorrowStart);
        // 理赔总数
        long totalCount = claimViewRepository.countByTenantId(tenantId);
        // 累计已结案赔付金额（已支付案件核定赔付金额之和）
        BigDecimal totalSettled = claimViewRepository.sumSettledAmountByStatusAndTenantId(ClaimStatus.PAID, tenantId);
        return new ClaimStatisticsResult(pendingCount, todayCount, totalCount, totalSettled);
    }

    // ==================== 搜索条件动态组装 ====================

    /**
     * 组装多条件搜索 Specification：租户隔离 + 可选条件精确匹配。
     */
    private Specification<ClaimView> buildSearchSpec(SearchClaimSummariesQuery query, ClaimStatus statusEnum,
            String tenantId) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (query.claimNo() != null) {
                predicates.add(cb.equal(root.get("claimNumber"), query.claimNo()));
            }
            if (query.policyId() != null) {
                predicates.add(cb.equal(root.get("policyId"), query.policyId()));
            }
            if (query.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), query.customerId()));
            }
            if (statusEnum != null) {
                predicates.add(cb.equal(root.get("status"), statusEnum));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 状态码 → 枚举（空安全解析，未匹配返回 null，不抛异常以兼容搜索语义）
     */
    private ClaimStatus resolveStatus(String statusCode) {
        if (statusCode == null) {
            return null;
        }
        return Arrays.stream(ClaimStatus.values())
                .filter(s -> s.getCode().equals(statusCode))
                .findFirst()
                .orElse(null);
    }
}
