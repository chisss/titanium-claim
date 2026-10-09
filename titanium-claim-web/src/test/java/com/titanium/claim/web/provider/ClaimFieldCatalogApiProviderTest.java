package com.titanium.claim.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.claim.api.response.fieldcatalog.ClaimFieldCatalogResponse;
import com.titanium.claim.api.response.fieldcatalog.ClaimFieldDescriptorResponse;
import com.titanium.claim.application.query.ClaimFieldCatalogApplicationService;
import com.titanium.claim.fieldcatalog.ClaimFieldCapability;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalog;
import com.titanium.claim.fieldcatalog.ClaimFieldDescriptor;
import com.titanium.claim.web.mapper.ClaimFieldCatalogWebMapperImpl;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.claim.fieldcatalog.ClaimFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.metadata.response.ApiResponse;

/**
 * Claim 字段目录契约实现测试。
 *
 * <p>租户不由入参传递，故用例必须先建立请求上下文——这是本类最重要的一条断言：
 * 契约响应里的租户必须**回显上下文**，而不是某个入参或常量。</p>
 */
class ClaimFieldCatalogApiProviderTest {

    private static final String TENANT = "tenant-1";

    @BeforeEach
    void setUp() {
        RequestContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldEchoTenantFromRequestContextAndMapCatalogToContract() {
        ClaimFieldCatalogApplicationService service = mock(ClaimFieldCatalogApplicationService.class);
        when(service.getCurrentCatalog(any())).thenReturn(sampleCatalog());
        ClaimFieldCatalogApiProvider provider =
                new ClaimFieldCatalogApiProvider(service, new ClaimFieldCatalogWebMapperImpl());
        LocalDate businessDate = LocalDate.of(2026, 10, 1);

        ApiResponse<ClaimFieldCatalogResponse> response = provider.getCurrentCatalog(businessDate);

        assertTrue(response.isSuccess());
        assertEquals(TENANT, response.getData().tenantId(), "租户必须来自请求上下文");
        assertEquals(businessDate, response.getData().businessDate(), "业务日期必须回显入参");
        assertEquals("2026.10.01.1", response.getData().catalogVersion());
        assertEquals(64, response.getData().contentHash().length());
        assertFalse(response.getData().fields().isEmpty());
        assertTrue(response.getData().fields().stream()
                .anyMatch(field -> field.fieldCode().equals("claim.name")
                        && field.capability().readable()
                        && !field.capability().proposable()));
        // 分析位必须**逐位**活着穿过领域 → MapStruct → 契约：MapStruct 对新增组件的漏映射
        // 是静默的（目标为 null/基本类型默认值），值断言是唯一能分辨「映射了」与「没映射」的判据。
        ClaimFieldDescriptorResponse name = response.getData().fields().stream()
                .filter(field -> field.fieldCode().equals("claim.name"))
                .findFirst()
                .orElseThrow();
        assertNotNull(name.analysis(), "分析位不得在契约映射中丢失");
        assertTrue(name.analysis().groupable(), "表里的 groupable=true 必须原样到达契约");
        assertFalse(name.analysis().aggregatable(), "TEXT 字段的 aggregatable=false 必须原样到达契约");
    }

    /** 一个含单字段的最小目录（内容哈希由领域 record 自行算出）。 */
    private static ClaimFieldCatalog sampleCatalog() {
        ClaimFieldDescriptor descriptor = ClaimFieldDescriptor.scalar(
                "claim.name",
                ClaimFieldObjectType.values()[0],
                PolicyFieldValueType.TEXT,
                "claim.field.name",
                ClaimFieldCapability.readOnly(),
                PolicyFieldSensitivityLevel.INTERNAL,
                PolicyFieldMaskingPolicy.NONE,
                FieldAnalysisCapability.of(true, false, true, true));
        return new ClaimFieldCatalog("2026.10.01.1", null, List.of(descriptor));
    }
}
