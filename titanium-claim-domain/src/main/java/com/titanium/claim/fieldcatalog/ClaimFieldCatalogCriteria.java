package com.titanium.claim.fieldcatalog;

import java.time.LocalDate;

/**
 * Claim 字段目录的业务时点查询条件。
 *
 * <p>🔴 <b>{@code tenantId} 是保留死参数，当前不参与目录隔离</b>（与 policy 域
 * {@code PolicyFieldCatalogCriteria} 同款口径，见 {@code G07/AC-10}）：字段目录是
 * <b>跨租户共享的平台元数据</b>，不分租户是<b>有意设计</b>而非遗漏；租户差异落在
 * <b>特征定义层</b>（feature-center 的 {@code FeatureDefinition}），不在目录层。</p>
 *
 * <p>一个「看似生效、实则被丢弃」的租户参数比没有这个参数<b>更危险</b>——它会让审计者与
 * 后来者认定隔离已实现。故此处显式写明，并由反向断言守护「零处据 tenantId 过滤目录」。</p>
 */
public record ClaimFieldCatalogCriteria(String tenantId, LocalDate businessDate) {

    public ClaimFieldCatalogCriteria {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ClaimFieldCatalogValidationException("租户ID不能为空");
        }
        if (businessDate == null) {
            throw new ClaimFieldCatalogValidationException("业务日期不能为空");
        }
        tenantId = tenantId.trim();
    }
}
