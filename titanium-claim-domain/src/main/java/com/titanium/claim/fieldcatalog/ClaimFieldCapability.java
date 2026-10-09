package com.titanium.claim.fieldcatalog;

/**
 * Claim 字段对取数/分析开放的能力。
 *
 * <p>六个位与 {@code t_claim_field_catalog} 表的能力列一一对应，是跨域消费方
 * （特征平台、取数 Adapter）判断「某字段能不能读、能不能提变更提案」的唯一来源。</p>
 *
 * <p>🔴 本域<b>不提供</b> policy 侧那样的 {@code proposable(...)} 与 {@code executable(...)}
 * 命名工厂：四域在 G07 范围内只有<b>读</b>需求（{@code G07/AC-02} 只发布只读契约），
 * 字段清单一律由 starter YAML 经目录表导入（{@code g08-06}），不存在「手写字段清单」这一场景。
 * 故本 record 只保留紧凑构造器（供「目录表行 → 领域对象」装配）与
 * {@link #readOnly()}（本域当前唯一实际使用的形态）。</p>
 */
public record ClaimFieldCapability(
        boolean readable,
        boolean proposable,
        boolean clearable,
        boolean executionSupported,
        boolean requiresObjectId,
        String changeTypeCode) {

    public ClaimFieldCapability {
        changeTypeCode = normalize(changeTypeCode);
        if (clearable && !proposable) {
            throw new ClaimFieldCatalogValidationException("可清空字段必须允许提交变更提案");
        }
        if (executionSupported && !proposable) {
            throw new ClaimFieldCatalogValidationException("可执行字段必须允许提交变更提案");
        }
        if (proposable && changeTypeCode == null) {
            throw new ClaimFieldCatalogValidationException("可提案字段必须配置业务变更类别");
        }
    }

    /** 创建只读字段能力：可读，且四个写侧位全关。 */
    public static ClaimFieldCapability readOnly() {
        return new ClaimFieldCapability(true, false, false, false, false, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
