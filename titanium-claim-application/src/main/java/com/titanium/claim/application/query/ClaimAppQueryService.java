package com.titanium.claim.application.query;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.application.mapper.ClaimReadModelMapper;
import com.titanium.claim.query.query.SearchClaimSummariesQuery;
import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.claim.query.result.ClaimStatisticsResult;
import com.titanium.claim.query.service.ClaimQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔读入口门面（application 层读用例入口）
 * <p>
 * 查读模型（CQRS 读侧 {@code t_claim_view}）：委托 {@link ClaimQueryService} 查询，经
 * {@link ClaimReadModelMapper} 组装为应用层读模型 {@link ClaimReadModel}（非对外契约，表现层再转 VO/Response）。
 * 不触碰写模型聚合、不发命令、不做编排（读写分离，ArchUnit 固化）。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimAppQueryService {

    private final ClaimQueryService    claimQueryService;
    private final ClaimReadModelMapper claimReadModelMapper;

    @Transactional(readOnly = true)
    public Optional<ClaimReadModel> getClaim(String claimId, String tenantId) {
        return claimQueryService.getClaimSummary(claimId, tenantId).map(this::toReadModel);
    }

    @Transactional(readOnly = true)
    public List<ClaimReadModel> getClaimsByCustomerId(String customerId, String tenantId) {
        return claimQueryService.getClaimSummariesByCustomerId(customerId, tenantId)
                .stream().map(this::toReadModel).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimReadModel> getClaimsByPolicyId(String policyId, String tenantId) {
        return claimQueryService.getClaimSummariesByPolicyId(policyId, tenantId)
                .stream().map(this::toReadModel).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimReadModel> getClaimsByStatus(String status, String tenantId) {
        return claimQueryService.getClaimSummariesByStatus(status, tenantId)
                .stream().map(this::toReadModel).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClaimReadModel> getAllClaims(String tenantId) {
        return claimQueryService.getAllClaimSummaries(tenantId)
                .stream().map(this::toReadModel).collect(Collectors.toList());
    }

    /**
     * 多条件搜索理赔案件（数据库侧过滤 + 分页，读入口）。
     * <p>
     * 委托 CQRS 读侧 {@link ClaimQueryService#searchClaimSummaries} 按组合条件 Specification 过滤并分页，
     * 取代历史 Controller 层内存过滤分页。
     * </p>
     *
     * @param query    搜索条件（字段均可空）
     * @param page     页码（从 0 开始）
     * @param size     每页条数
     * @param tenantId 租户ID
     * @return 命中页的应用层读模型列表
     */
    @Transactional(readOnly = true)
    public List<ClaimReadModel> searchClaims(SearchClaimSummariesQuery query, int page, int size, String tenantId) {
        return claimQueryService.searchClaimSummaries(query, page, size, tenantId)
                .stream().map(this::toReadModel).collect(Collectors.toList());
    }

    /**
     * 查询理赔聚合统计（管理后台看板读入口）。
     * <p>
     * 委托 CQRS 读侧 {@link ClaimQueryService} 聚合读模型（{@code t_claim_view}）：待处理数、今日报案数、
     * 理赔总数、累计已结案赔付金额。强制携带 {@code tenantId} 保证多租户隔离。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 理赔统计结果
     */
    @Transactional(readOnly = true)
    public ClaimStatisticsResult getStatistics(String tenantId) {
        return claimQueryService.getStatistics(tenantId);
    }

    /**
     * 读模型查询结果 → 应用层读模型。
     * <p>
     * 读侧读模型（ClaimQueryResult）→ 应用层读模型（ClaimReadModel）的内部装配，经
     * {@link ClaimReadModelMapper} 声明式映射：状态/理赔类型枚举经空安全 {@code @Named} 方法收敛为 code 与中文描述。
     * </p>
     */
    private ClaimReadModel toReadModel(ClaimQueryResult result) {
        return claimReadModelMapper.toReadModel(result);
    }
}
