package com.titanium.claim.query.query;

/**
 * 理赔案件多条件搜索查询入参（CQRS 读侧服务级入参，非 QueryBus 派发）
 * <p>
 * 组合条件均为可选（null 表示不参与过滤）：理赔编号、保单ID、客户ID、状态码。
 * 由 {@code ClaimQueryService.searchClaimSummaries} 下沉为数据库侧 Specification 过滤 + 分页，
 * 取代历史 Controller 层内存过滤。
 * </p>
 *
 * @param claimNo    理赔编号（精确匹配，可空）
 * @param policyId   保单ID（精确匹配，可空）
 * @param customerId 客户ID（精确匹配，可空）
 * @param status     理赔状态码（精确匹配，可空；非法码返回空结果而非抛异常，兼容历史语义）
 */
public record SearchClaimSummariesQuery(
        String claimNo,
        String policyId,
        String customerId,
        String status) {
}
