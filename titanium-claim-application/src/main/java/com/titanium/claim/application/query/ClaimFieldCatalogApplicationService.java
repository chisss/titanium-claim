package com.titanium.claim.application.query;

import java.util.List;

import org.springframework.stereotype.Service;

import com.titanium.claim.fieldcatalog.ClaimFieldCatalog;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogCriteria;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogValidationException;
import com.titanium.claim.query.repository.ClaimFieldCatalogViewRepository;
import com.titanium.claim.query.view.ClaimFieldCatalogView;

import lombok.RequiredArgsConstructor;

/**
 * 客户字段目录应用查询服务（读用例入口门面）。
 *
 * <p>只做三件事：校验条件 → 取全量目录行 → 交装配器。业务规则（字段不变量、内容哈希）全在
 * 领域 record 与装配器中，本类不含任何判断分支。</p>
 *
 * <p>🔴 <b>查询条件中的 {@code tenantId} 是死参数</b>（{@code G07/AC-10}）：目录是跨租户共享的
 * 平台元数据，不按租户过滤是<strong>有意设计</strong>，故本类<strong>不</strong>把租户下推到仓储。
 * 租户差异落在特征定义层（feature-center 的 {@code FeatureDefinition}）。</p>
 */
@Service
@RequiredArgsConstructor
public class ClaimFieldCatalogApplicationService {

    /**
     * 当前目录版本。
     *
     * <p>目录表**没有版本列**（版本落库与业务时点快照是 {@code g07-04} 的任务标题，届时一并 ALTER），
     * 故当前版本由本常量声明。内容若变更，{@code contentHash} 会随字段内容自动变化，
     * 而版本号是人类可读的发布标识，需要一起改。</p>
     */
    public static final String CURRENT_CATALOG_VERSION = "2026.10.01.1";

    private final ClaimFieldCatalogViewRepository viewRepository;
    private final ClaimFieldCatalogAssembler assembler;

    /**
     * 查询指定业务时点生效的权威字段目录。
     *
     * @throws ClaimFieldCatalogValidationException 条件为空，或目录表尚未初始化
     */
    public ClaimFieldCatalog getCurrentCatalog(ClaimFieldCatalogCriteria criteria) {
        if (criteria == null) {
            throw new ClaimFieldCatalogValidationException("字段目录查询条件不能为空");
        }
        List<ClaimFieldCatalogView> rows = viewRepository.findAllByOrderByFieldCodeAsc();
        return assembler.assemble(CURRENT_CATALOG_VERSION, rows);
    }
}
