package com.titanium.claim.infrastructure.adapter.clause;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.claim.port.clause.ClauseServicePort;
import com.titanium.clause.api.ClauseApi;
import com.titanium.clause.api.response.CoverageResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 条款服务 Adapter（对端域：clause）
 * <p>
 * 实现 {@link ClauseServicePort}：调用条款域 {@link ClauseApi} Feign，将下游契约
 * {@link CoverageResponse} 翻译为领域摘要 {@code CoverageInfo}（防腐：领域不依赖对端 api 类型）。
 * 责任校验（CLAIM-4）的条款责任取数来源：application 编排先经 policy 域取条款ID，
 * 再经本 Adapter 取保险责任列表。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClauseServiceAdapter implements ClauseServicePort {

    private final ClauseApi clauseApi;

    @Override
    public List<CoverageInfo> fetchCoverages(String clauseId, String tenantId) {
        log.info("查询条款保险责任, clauseId={}, tenantId={}", clauseId, tenantId);
        // ClauseApi 契约返回裸列表（无 ApiResponse 信封，下游 api 层契约现状）
        List<CoverageResponse> coverages = clauseApi.getCoveragesByClauseId(clauseId, tenantId);
        if (coverages == null) {
            return List.of();
        }
        return coverages.stream()
                .map(this::toCoverageInfo)
                .toList();
    }

    /**
     * 下游契约 → 领域摘要（仅承载责任判定要素，条款域按有效条款过滤返回）
     */
    private CoverageInfo toCoverageInfo(CoverageResponse coverage) {
        return new CoverageInfo(
                coverage.getCoverageId(),
                coverage.getCoverageCode(),
                coverage.getCoverageName(),
                coverage.getWaitingPeriodDays());
    }
}
