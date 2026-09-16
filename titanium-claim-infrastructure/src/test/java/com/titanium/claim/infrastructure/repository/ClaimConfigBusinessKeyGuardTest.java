package com.titanium.claim.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.aggregate.ClaimFlowTemplate;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.infrastructure.entity.ClaimFlowTemplateDO;
import com.titanium.claim.infrastructure.mapper.ClaimConfigPersistenceMapper;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimFlowTemplateRepository;

/**
 * 理赔配置「新建即静默覆盖」护栏测试（🔴 D-501-53）
 * <p>
 * 五组按业务键唯一的配置（流程模板/赔付规则/快赔规则/单证模板/时限规则）仓储 {@code save()} 共用
 * 同一控制流，本类以流程模板为代表锁死两条判据：
 * </p>
 * <ul>
 *   <li><b>业务键已被别的聚合占用</b>（新增路径传入全新聚合ID）→ 抛
 *       {@link BusinessException}（错误码 30001023），<b>不落库</b>——否则用户的「新建」
 *       会静默改写别人的既有规则，且行数不变、无提示、用户无从自证；</li>
 *   <li><b>同一聚合的更新</b>（业务键命中的正是自己）→ 正常复用原主键与创建时间，不误伤。</li>
 * </ul>
 * <p>
 * 五个 adapter 的 save() 差异仅在实体名与业务键组合，护栏类为同一份实现，
 * {@code ClaimConfigBusinessKeyGuardTest} 覆盖其判据本体。
 * </p>
 */
class ClaimConfigBusinessKeyGuardTest {

    private static final String TENANT_ID      = "T-1";
    private static final String INSURANCE_LINE = "MEDICAL";
    private static final String CLAIM_TYPE     = "MEDICAL_REIMBURSE";

    /** 库中已存在的行（属于「别人的」聚合） */
    private static final String EXISTING_TEMPLATE_ID = "TEMPLATE-EXISTING";

    private JpaClaimFlowTemplateRepository          jpaRepository;
    private JpaClaimFlowTemplateRepositoryAdapter   adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(JpaClaimFlowTemplateRepository.class);
        ClaimConfigPersistenceMapper mapper = mock(ClaimConfigPersistenceMapper.class);
        when(mapper.toDO(any(ClaimFlowTemplate.class))).thenReturn(new ClaimFlowTemplateDO());
        adapter = new JpaClaimFlowTemplateRepositoryAdapter(jpaRepository, mapper);
    }

    private void givenBusinessKeyOccupied() {
        ClaimFlowTemplateDO occupied = new ClaimFlowTemplateDO();
        occupied.setId("ROW-1");
        occupied.setTemplateId(EXISTING_TEMPLATE_ID);
        when(jpaRepository.findByTenantIdAndInsuranceLineAndClaimType(TENANT_ID, INSURANCE_LINE, CLAIM_TYPE))
                .thenReturn(Optional.of(occupied));
    }

    private ClaimFlowTemplate template(String templateId) {
        return ClaimFlowTemplate.create(templateId, TENANT_ID, INSURANCE_LINE, CLAIM_TYPE,
                List.of("REPORT", "APPROVAL"), null, "理赔专员", null);
    }

    @Test
    @DisplayName("业务键已被其它配置占用时新增：显式失败且不落库，不得静默覆盖")
    void shouldRejectCreateOnOccupiedBusinessKey() {
        givenBusinessKeyOccupied();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.save(template("TEMPLATE-NEW")));

        assertEquals("30001023", exception.getErrorCode(), "须携带「业务键已存在」错误码");
        verify(jpaRepository, never()).save(any(ClaimFlowTemplateDO.class));
    }

    @Test
    @DisplayName("同一聚合的更新（业务键命中的就是自己）：放行并复用原主键与创建时间")
    void shouldAllowUpdateOfSameAggregate() {
        givenBusinessKeyOccupied();

        adapter.save(template(EXISTING_TEMPLATE_ID));

        verify(jpaRepository).save(any(ClaimFlowTemplateDO.class));
    }
}
