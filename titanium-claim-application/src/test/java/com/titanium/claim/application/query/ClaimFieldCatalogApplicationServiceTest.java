package com.titanium.claim.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.claim.fieldcatalog.ClaimFieldCatalog;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogCriteria;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogValidationException;
import com.titanium.claim.query.repository.ClaimFieldCatalogViewRepository;
import com.titanium.claim.query.view.ClaimFieldCatalogView;
import com.titanium.metadata.enums.claim.fieldcatalog.ClaimFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

/**
 * Claim 字段目录应用查询服务测试。
 *
 * <p>只 mock 仓储（Spring Data 接口），装配器与领域 record 一律用**真对象**——目录的内容哈希、
 * 排序与不变量都属被测行为，替身掉它们等于把要验的东西换成了桩。</p>
 */
class ClaimFieldCatalogApplicationServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 1);

    private static final ClaimFieldObjectType OBJECT_TYPE = ClaimFieldObjectType.values()[0];

    private final ClaimFieldCatalogViewRepository repository = mock(ClaimFieldCatalogViewRepository.class);

    private final ClaimFieldCatalogApplicationService service =
            new ClaimFieldCatalogApplicationService(repository, new ClaimFieldCatalogAssembler());

    @Test
    void shouldAssembleCatalogFromTableRowsWithSelfConsistentHash() {
        when(repository.findAllByOrderByFieldCodeAsc())
                .thenReturn(List.of(row("claim.name"), row("claim.idNo")));

        ClaimFieldCatalog catalog = service.getCurrentCatalog(criteria());

        assertEquals(ClaimFieldCatalogApplicationService.CURRENT_CATALOG_VERSION, catalog.catalogVersion());
        assertEquals(2, catalog.fields().size());
        assertEquals("claim.idNo", catalog.fields().getFirst().fieldCode(), "目录应按字段码稳定排序");
        assertEquals(64, catalog.contentHash().length(), "内容哈希由目录自行计算（表无该列）");
        assertEquals("claim.name", catalog.requireField("claim.name").fieldCode());
    }

    @Test
    void shouldFailClosedWhenCatalogTableIsNotSeeded() {
        when(repository.findAllByOrderByFieldCodeAsc()).thenReturn(List.of());

        ClaimFieldCatalogValidationException exception =
                assertThrows(ClaimFieldCatalogValidationException.class, () -> service.getCurrentCatalog(criteria()));

        assertTrue(exception.getMessage().contains("未初始化"),
                "空表必须报「目录未初始化」而不是泛化失败——否则消费方会把「没灌种子」误读为「该域没有可分析字段」");
    }

    @Test
    void shouldRejectNullCriteria() {
        assertThrows(ClaimFieldCatalogValidationException.class, () -> service.getCurrentCatalog(null));
    }

    @Test
    void shouldApplyDescriptorInvariantsToTableRows() {
        // 表里出现自相矛盾的行（集合字段却无对象标识）必须被领域校验拦下，不得原样放行到消费方
        ClaimFieldCatalogView broken = row("claim.relation.name");
        broken.setCollection(true);
        when(repository.findAllByOrderByFieldCodeAsc()).thenReturn(List.of(broken));

        assertThrows(ClaimFieldCatalogValidationException.class, () -> service.getCurrentCatalog(criteria()));
    }

    @Test
    void shouldRejectIllegalAnalysisBitsFromTableRow() {
        // 表里声明 TEXT 却 aggregatable=true ⇒ 违反分析位规则 2，装配时必须当场拒绝（fail-closed），
        // 而不是替运营「修正」成看起来合理的值。
        ClaimFieldCatalogView broken = row("claim.name");
        broken.setAnalyzable(true);
        broken.setAggregatable(true);
        broken.setGroupable(true);
        broken.setFilterable(true);
        when(repository.findAllByOrderByFieldCodeAsc()).thenReturn(List.of(broken));

        assertThrows(ClaimFieldCatalogValidationException.class, () -> service.getCurrentCatalog(criteria()));
    }

    @Test
    void shouldReadAnalysisBitsFromColumnsRatherThanDerivingThem() {
        // 手工构造**不可推导**的合法组合：非集合 TEXT 却 groupable=true——若装配器改成按
        // valueType/collection 就地推导，这一位会被抹成 false，下面的断言随之失败。
        ClaimFieldCatalogView row = row("claim.note");
        row.setAnalyzable(true);
        row.setAggregatable(false);
        row.setGroupable(true);
        row.setFilterable(true);
        when(repository.findAllByOrderByFieldCodeAsc()).thenReturn(List.of(row));

        FieldAnalysisCapability analysis = service.getCurrentCatalog(criteria()).requireField("claim.note").analysis();

        assertTrue(analysis.groupable(), "分析位必须逐列直读：非集合字段的 groupable 不得被推导抹平");
        assertFalse(analysis.aggregatable(), "TEXT 字段的 aggregatable 必须保持表里的 false");
        assertTrue(analysis.filterable());
    }

    private static ClaimFieldCatalogCriteria criteria() {
        return new ClaimFieldCatalogCriteria("T1", BUSINESS_DATE);
    }

    /** 造一行「只读标量字段」，各用例只改自己关心的列。 */
    private static ClaimFieldCatalogView row(String fieldCode) {
        ClaimFieldCatalogView row = new ClaimFieldCatalogView();
        row.setFieldCode(fieldCode);
        row.setObjectType(OBJECT_TYPE);
        row.setValueType(PolicyFieldValueType.TEXT);
        row.setLabelKey("field." + fieldCode);
        row.setCollection(false);
        row.setReadable(true);
        row.setSensitivity(PolicyFieldSensitivityLevel.INTERNAL);
        row.setMaskingPolicy(PolicyFieldMaskingPolicy.NONE);
        // 分析位显式取「全 false」（合法表达：不参与分析）；boolean 默认值虽也是 false，
        // 但显式写出后本夹具的语义是声明而非巧合。
        row.setAnalyzable(false);
        row.setAggregatable(false);
        row.setGroupable(false);
        row.setFilterable(false);
        return row;
    }
}
