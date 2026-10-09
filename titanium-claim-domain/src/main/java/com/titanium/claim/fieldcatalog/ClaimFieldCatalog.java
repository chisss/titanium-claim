package com.titanium.claim.fieldcatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claim 对外发布的不可变字段目录。
 *
 * <p>构造即校验：版本非空、字段非空、无空元素、按字段码排序、字段码唯一、内容哈希自洽
 * （传入哈希与实算不符即拒，防手工改表后哈希漂移）。</p>
 *
 * <p>🔴 <b>本域不提供静态的「首版目录」工厂</b>——这正是 G07 要修的「常量代替配置」病：
 * policy 域的 19 个字段曾以字面量清单硬编码在目录类内，加字段必须改代码。四域字段清单一律由
 * starter YAML 经 {@code t_claim_field_catalog} 表导入（{@code g08-06}），领域对象只负责
 * 「表行 → 目录」的装配与校验。</p>
 *
 * <p><b>本类出现任何字面量字段清单即为回归</b>，由校验脚本的反向断言守护。该判据的扫描口径是
 * <b>全文（含注释）</b>，故本文档刻意不写出该写法的具体 API 名——这是有意的，不是疏漏。</p>
 */
public record ClaimFieldCatalog(String catalogVersion, String contentHash, List<ClaimFieldDescriptor> fields) {

    public ClaimFieldCatalog {
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new ClaimFieldCatalogValidationException("字段目录版本不能为空");
        }
        if (fields == null || fields.isEmpty()) {
            throw new ClaimFieldCatalogValidationException("字段目录不能为空");
        }
        catalogVersion = catalogVersion.trim();
        if (fields.stream().anyMatch(Objects::isNull)) {
            throw new ClaimFieldCatalogValidationException("字段目录不能包含空字段");
        }
        fields = fields.stream().sorted(Comparator.comparing(ClaimFieldDescriptor::fieldCode)).toList();
        validateUniqueFieldCodes(fields);
        String calculatedHash = calculateHash(catalogVersion, fields);
        if (contentHash != null && !contentHash.isBlank() && !calculatedHash.equals(contentHash)) {
            throw new ClaimFieldCatalogValidationException("字段目录内容哈希不一致");
        }
        contentHash = calculatedHash;
    }

    /** 按稳定字段码查找字段；未命中即拒，不静默返回 null。 */
    public ClaimFieldDescriptor requireField(String fieldCode) {
        return fields.stream()
                .filter(field -> field.fieldCode().equals(fieldCode))
                .findFirst()
                .orElseThrow(() -> new ClaimFieldCatalogValidationException("字段目录不存在字段: " + fieldCode));
    }

    private static void validateUniqueFieldCodes(List<ClaimFieldDescriptor> fields) {
        Set<String> fieldCodes = new HashSet<>();
        for (ClaimFieldDescriptor field : fields) {
            if (!fieldCodes.add(field.fieldCode())) {
                throw new ClaimFieldCatalogValidationException("字段目录编码重复: " + field.fieldCode());
            }
        }
    }

    private static String calculateHash(String version, List<ClaimFieldDescriptor> fields) {
        String canonical = version + "\n"
                + fields.stream().map(ClaimFieldDescriptor::canonicalForm).collect(Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
