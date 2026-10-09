package com.titanium.claim.fieldcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.claim.fieldcatalog.ClaimFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

/**
 * Claim 字段目录领域模型测试。
 *
 * <p>🔴 本域<b>无</b>硬编码字段清单（字段目录由 starter YAML 经 {@code t_claim_field_catalog}
 * 表导入），故用例一律<b>自建 descriptor</b> 组装目录、不依赖任何内置清单——若将来有人给
 * 目录类加回静态的「首版目录」常量，本类<b>不会</b>因此变绿（那是回归，由校验脚本的反向断言拦截）。</p>
 *
 * <p>🔴 验证「非分析位」规则的用例一律传 {@link #INERT_ANALYSIS}（全 false）：
 * 若传 {@code null} 或非法组合，异常会改从分析位判空/四条不变量抛出，用例照样「通过」
 * 却不再验证它声称验证的那条规则——判据静默退化。</p>
 */
class ClaimFieldCatalogTest {

    private static final String VERSION = "2026.10.01.1";

    /** 取本域任一对象类型：用例不依赖具体枚举值（值域由建表时的字段对象类型枚举各自锁定）。 */
    private static final ClaimFieldObjectType OBJECT_TYPE = ClaimFieldObjectType.values()[0];

    /** 在所有值类型 × 集合组合下都合法的分析位（全 false = 不参与分析），用于隔离其它规则的用例。 */
    private static final FieldAnalysisCapability INERT_ANALYSIS = FieldAnalysisCapability.none();

    /** TEXT 标量的合法可分析组合（不可聚合、可分组、可筛选），用于让哈希用例真实覆盖分析位分量。 */
    private static final FieldAnalysisCapability TEXT_ANALYSIS = FieldAnalysisCapability.of(true, false, true, true);

    @Test
    void shouldBuildCatalogAndKeepHashStableWhenInputOrderChanges() {
        ClaimFieldDescriptor name = scalar("claim.name", PolicyFieldSensitivityLevel.INTERNAL);
        ClaimFieldDescriptor idNo = scalar("claim.idNo", PolicyFieldSensitivityLevel.RESTRICTED);
        ClaimFieldDescriptor relation = ClaimFieldDescriptor.collectionField(
                "claim.relation.name", OBJECT_TYPE, PolicyFieldValueType.TEXT, "claim.field.relation.name",
                "relationshipId", ClaimFieldCapability.readOnly(), PolicyFieldSensitivityLevel.INTERNAL,
                PolicyFieldMaskingPolicy.NONE, TEXT_ANALYSIS);

        ClaimFieldCatalog catalog = new ClaimFieldCatalog(VERSION, null, List.of(name, idNo, relation));

        assertEquals(3, catalog.fields().size());
        assertEquals("claim.idNo", catalog.fields().getFirst().fieldCode(), "目录应按字段码排序");
        assertEquals(64, catalog.contentHash().length());
        assertEquals("relationshipId", catalog.requireField("claim.relation.name").objectIdentityField());

        List<ClaimFieldDescriptor> reversed = new ArrayList<>(catalog.fields());
        Collections.reverse(reversed);
        ClaimFieldCatalog restored = new ClaimFieldCatalog(VERSION, catalog.contentHash(), reversed);

        assertEquals(catalog.contentHash(), restored.contentHash(), "输入顺序变化不得改变内容哈希");
        assertEquals(catalog.fields(), restored.fields());
    }

    @Test
    void shouldFoldAnalysisBitsIntoContentHash() {
        // 分析位是目录内容的一部分（G07/g07-03a 裁示）：同一字段仅分析位不同即应改变内容哈希，
        // 否则「表里的能力口径变了」不会触发任何变更信号。
        ClaimFieldDescriptor plain = scalar("claim.name", PolicyFieldSensitivityLevel.INTERNAL);
        ClaimFieldDescriptor sameCodeDifferentBits = new ClaimFieldDescriptor(
                "claim.name", OBJECT_TYPE, PolicyFieldValueType.TEXT, "field.claim.name", false, null,
                ClaimFieldCapability.readOnly(), PolicyFieldSensitivityLevel.INTERNAL,
                PolicyFieldMaskingPolicy.NONE, null, FieldAnalysisCapability.none());

        assertNotEquals(
                new ClaimFieldCatalog(VERSION, null, List.of(plain)).contentHash(),
                new ClaimFieldCatalog(VERSION, null, List.of(sameCodeDifferentBits)).contentHash(),
                "仅分析位不同也必须改变内容哈希");
    }

    @Test
    void shouldRejectEmptyCatalogBlankVersionDuplicateCodesAndMismatchedHash() {
        ClaimFieldDescriptor name = scalar("claim.name", PolicyFieldSensitivityLevel.INTERNAL);

        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCatalog(VERSION, null, List.of()));
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCatalog("  ", null, List.of(name)));
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCatalog(VERSION, null, List.of(name, name)));
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCatalog(VERSION, "0".repeat(64), List.of(name)));
    }

    @Test
    void shouldFailClosedWhenFieldCodeUnknown() {
        ClaimFieldCatalog catalog = new ClaimFieldCatalog(
                VERSION, null, List.of(scalar("claim.name", PolicyFieldSensitivityLevel.INTERNAL)));

        ClaimFieldCatalogValidationException exception = assertThrows(
                ClaimFieldCatalogValidationException.class, () -> catalog.requireField("claim.nope"));

        assertNotEquals("", exception.getErrorCodeValue());
        assertTrue(exception.getMessage().contains("claim.nope"), "失败信息须含未命中的字段码");
    }

    @Test
    void shouldRejectSensitiveFieldWithoutMaskingPolicy() {
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> ClaimFieldDescriptor.scalar("claim.idNo", OBJECT_TYPE, PolicyFieldValueType.TEXT,
                        "claim.field.idNo", ClaimFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.RESTRICTED, PolicyFieldMaskingPolicy.NONE, INERT_ANALYSIS));
    }

    @Test
    void shouldRejectCollectionWithoutIdentityFieldAndIllegalFieldCodes() {
        // 集合字段缺业务对象标识
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> ClaimFieldDescriptor.collectionField("claim.relation.name", OBJECT_TYPE,
                        PolicyFieldValueType.TEXT, "claim.field.relation.name", null,
                        ClaimFieldCapability.readOnly(), PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE, INERT_ANALYSIS));
        // 非集合字段却带业务对象标识
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldDescriptor("claim.name", OBJECT_TYPE, PolicyFieldValueType.TEXT,
                        "claim.field.name", false, "claimId", ClaimFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE, null,
                        INERT_ANALYSIS));
        // 字段码格式非法（无点分段 / 段首大写）
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> ClaimFieldDescriptor.scalar("name", OBJECT_TYPE, PolicyFieldValueType.TEXT,
                        "claim.field.name", ClaimFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE, INERT_ANALYSIS));
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> ClaimFieldDescriptor.scalar("Bad.Code", OBJECT_TYPE, PolicyFieldValueType.TEXT,
                        "claim.field.name", ClaimFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL, PolicyFieldMaskingPolicy.NONE, INERT_ANALYSIS));
    }

    @Test
    void shouldRejectCapabilityCombinationsThatCannotBeExecuted() {
        // 可清空 / 可执行 但不可提案
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCapability(true, false, true, false, false, null));
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCapability(true, false, false, true, false, null));
        // 可提案却无业务变更类别
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCapability(true, true, false, false, false, null));

        assertEquals(new ClaimFieldCapability(true, false, false, false, false, null),
                ClaimFieldCapability.readOnly());
    }

    @Test
    void shouldValidateCriteriaAndEchoTenantIdAsDeadParameter() {
        ClaimFieldCatalogCriteria criteria =
                new ClaimFieldCatalogCriteria(" T1 ", LocalDate.of(2026, 10, 1));

        // 死参数口径：租户ID 只做规范化回显，不参与任何目录过滤（见 record javadoc）
        assertEquals("T1", criteria.tenantId());
        assertEquals(LocalDate.of(2026, 10, 1), criteria.businessDate());
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCatalogCriteria("  ", LocalDate.of(2026, 10, 1)));
        assertThrows(ClaimFieldCatalogValidationException.class,
                () -> new ClaimFieldCatalogCriteria("T1", null));
    }

    private static ClaimFieldDescriptor scalar(String code, PolicyFieldSensitivityLevel sensitivity) {
        PolicyFieldMaskingPolicy masking =
                sensitivity.requiresMasking() ? PolicyFieldMaskingPolicy.PARTIAL_TEXT : PolicyFieldMaskingPolicy.NONE;
        return ClaimFieldDescriptor.scalar(code, OBJECT_TYPE, PolicyFieldValueType.TEXT,
                "field." + code, ClaimFieldCapability.readOnly(), sensitivity, masking, TEXT_ANALYSIS);
    }
}
