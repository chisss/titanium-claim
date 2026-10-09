package com.titanium.claim.application.query;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.claim.fieldcatalog.ClaimFieldCapability;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalog;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogValidationException;
import com.titanium.claim.fieldcatalog.ClaimFieldDescriptor;
import com.titanium.claim.query.view.ClaimFieldCatalogView;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

/**
 * 客户字段目录装配器：目录表行 → 领域权威目录。
 *
 * <p>单独成类（而非内联进应用服务）有两条依据：① 单次组装 11 个组件，属项目红线
 * 「&gt;5 字段的对象组装必须抽象 Assembler/Builder」；② 装配本身是<strong>可脱离容器 new 直测</strong>
 * 的纯逻辑，独立后无需 Spring 上下文即可验证边界。</p>
 *
 * <p>🔴 <b>空表即拒（fail-closed）</b>：{@code t_claim_field_catalog} 无行意味着
 * 目录<strong>尚未初始化</strong>（starter 的 {@code seed-mode=init} 未跑，见 {@code g08-06}），
 * 此时返回空目录会让消费方把「没有字段可分析」误读为「这个域没有可分析字段」。
 * 领域目录 record 自身也拒绝空清单，此处提前给出可定位的错误信息。</p>
 */
@Component
public class ClaimFieldCatalogAssembler {

    /**
     * 表行 → 权威目录。
     *
     * @param catalogVersion 当前目录版本（表无版本列，由调用方声明）
     * @param rows           目录表全量行，须已按字段码稳定排序
     * @return 校验通过、内容哈希自洽的领域目录
     */
    public ClaimFieldCatalog assemble(String catalogVersion, List<ClaimFieldCatalogView> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ClaimFieldCatalogValidationException(
                    "字段目录未初始化：t_claim_field_catalog 无任何行，须由 starter 在 seed-mode=init 导入种子");
        }
        List<ClaimFieldDescriptor> descriptors = rows.stream().map(this::toDescriptor).toList();
        return new ClaimFieldCatalog(catalogVersion, null, descriptors);
    }

    private ClaimFieldDescriptor toDescriptor(ClaimFieldCatalogView row) {
        return new ClaimFieldDescriptor(
                row.getFieldCode(),
                row.getObjectType(),
                row.getValueType(),
                row.getLabelKey(),
                row.isCollection(),
                row.getObjectIdentityField(),
                toCapability(row),
                row.getSensitivity(),
                row.getMaskingPolicy(),
                row.getDeprecatedAt(),
                toAnalysis(row));
    }

    private ClaimFieldCapability toCapability(ClaimFieldCatalogView row) {
        return new ClaimFieldCapability(
                row.isReadable(),
                row.isProposable(),
                row.isClearable(),
                row.isExecutionSupported(),
                row.isRequiresObjectId(),
                row.getChangeTypeCode());
    }

    /**
     * 读 4 个分析位（G07/g07-03）。
     *
     * <p>🔴 <b>逐列直读，不做任何兜底或推断</b>：表列是这四位唯一的权威来源，若此处按
     * {@code valueType} 就地推导，则运营在表里显式声明的口径会被代码覆盖，且「表写了什么」
     * 与「系统认什么」从此分叉。表里写了非法组合（如 TEXT 却 aggregatable=1）时，
     * 由 {@code ClaimFieldDescriptor} 的紧凑构造器当场拒绝——fail-closed，
     * 而不是替运营"修正"成一个看起来合理的值。</p>
     */
    private FieldAnalysisCapability toAnalysis(ClaimFieldCatalogView row) {
        return new FieldAnalysisCapability(
                row.isAnalyzable(),
                row.isAggregatable(),
                row.isGroupable(),
                row.isFilterable());
    }
}
