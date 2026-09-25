package com.titanium.claim.infrastructure.adapter.clause;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.claim.port.clause.ClauseServicePort;
import com.titanium.clause.api.ClauseApi;
import com.titanium.clause.api.response.clause.CoverageResponse;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;

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
        // ClauseApi 契约统一返回 ApiResponse 信封：先判 isSuccess 再取 data。
        // 失败分支必须显式抛错——若把「错误信封」静默当作空列表，会被上层读成「无保险责任」而误判拒赔。
        ApiResponse<List<CoverageResponse>> response = clauseApi.getCoveragesByClauseId(clauseId);
        if (!response.isSuccess()) {
            log.error("查询条款保险责任失败, clauseId={}, error={}", clauseId, response.getMessage());
            throw new DomainException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "查询条款保险责任失败: " + response.getMessage());
        }
        List<CoverageResponse> coverages = response.getData();
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
