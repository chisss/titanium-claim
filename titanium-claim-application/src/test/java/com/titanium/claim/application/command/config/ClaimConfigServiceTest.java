package com.titanium.claim.application.command.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.aggregate.ClaimBlacklist;
import com.titanium.claim.aggregate.ClaimFlowTemplate;
import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.application.model.config.ClaimBlacklistConfigRequest;
import com.titanium.claim.application.model.config.ClaimFlowTemplateConfigRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.enums.config.BlacklistStatus;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimBlacklistRepository;
import com.titanium.claim.repository.ClaimFlowTemplateRepository;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;

/**
 * 理赔配置子域写服务测试（application/command/config）
 * <p>
 * 验证薄门面模式的编排行为：新增走聚合工厂 + 仓储 save；更新/行为操作先 findById，
 * 未找到抛 {@code CLAIM_CONFIG_NOT_FOUND}；撤销/暂停等行为委托聚合返回新实例落库。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("理赔配置写服务")
class ClaimConfigServiceTest {

    private static final String TENANT_ID = "tenant-001";

    @Mock
    private ClaimFlowTemplateRepository   flowTemplateRepository;
    @Mock
    private ClaimBlacklistRepository      blacklistRepository;
    @Mock
    private ClaimHospitalNetworkRepository hospitalRepository;
    @Mock
    private TenantContext                 tenantContext;

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
    }

    @Nested
    @DisplayName("流程模板 FlowTemplateConfigService")
    class FlowTemplateServiceTest {

        @Test
        @DisplayName("templateId 空 → 聚合工厂创建并落库，返回雪花 ID")
        void shouldCreateWhenIdBlank() {
            ClaimFlowTemplateConfigRequest request = new ClaimFlowTemplateConfigRequest();
            request.setInsuranceLine("MEDICAL");
            request.setClaimType("MEDICAL_REIMBURSE");
            request.setStageSequence(List.of("报案", "核赔", "给付"));
            request.setStageTimeLimitHours(Map.of("核赔", 48));
            request.setResponsibleRole("理赔专员");
            request.setMandatoryCheckpoints(List.of("核赔"));

            FlowTemplateConfigService service = new FlowTemplateConfigService(flowTemplateRepository, tenantContext);
            String templateId = service.saveFlowTemplate(request);

            assertThat(templateId).isNotBlank();
            ArgumentCaptor<ClaimFlowTemplate> captor = ArgumentCaptor.forClass(ClaimFlowTemplate.class);
            verify(flowTemplateRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
            assertThat(captor.getValue().getStageSequence()).containsExactly("报案", "核赔", "给付");
        }

        @Test
        @DisplayName("templateId 非空且存在 → update 全量覆盖后落库")
        void shouldUpdateWhenFound() {
            ClaimFlowTemplate existing = ClaimFlowTemplate.create("tpl-1", TENANT_ID, "MEDICAL",
                    "MEDICAL_REIMBURSE", List.of("报案", "核赔"), Map.of("核赔", 48), "理赔专员", null);
            when(flowTemplateRepository.findById(TENANT_ID, "tpl-1")).thenReturn(Optional.of(existing));

            ClaimFlowTemplateConfigRequest request = new ClaimFlowTemplateConfigRequest();
            request.setTemplateId("tpl-1");
            request.setInsuranceLine("PET");
            request.setClaimType("PET_MEDICAL");
            request.setStageSequence(List.of("报案", "给付"));
            request.setStageTimeLimitHours(Map.of("报案", 24));

            FlowTemplateConfigService service = new FlowTemplateConfigService(flowTemplateRepository, tenantContext);
            String templateId = service.saveFlowTemplate(request);

            assertThat(templateId).isEqualTo("tpl-1");
            ArgumentCaptor<ClaimFlowTemplate> captor = ArgumentCaptor.forClass(ClaimFlowTemplate.class);
            verify(flowTemplateRepository).save(captor.capture());
            assertThat(captor.getValue().getInsuranceLine()).isEqualTo("PET");
        }

        @Test
        @DisplayName("templateId 非空但不存在 → 抛 CLAIM_CONFIG_NOT_FOUND")
        void shouldThrowWhenUpdateTargetMissing() {
            when(flowTemplateRepository.findById(TENANT_ID, "tpl-404")).thenReturn(Optional.empty());

            ClaimFlowTemplateConfigRequest request = new ClaimFlowTemplateConfigRequest();
            request.setTemplateId("tpl-404");
            request.setStageSequence(List.of("报案"));

            FlowTemplateConfigService service = new FlowTemplateConfigService(flowTemplateRepository, tenantContext);
            assertThatThrownBy(() -> service.saveFlowTemplate(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("流程模板不存在");
        }

        @Test
        @DisplayName("删除委托仓储逻辑删除")
        void shouldDelete() {
            FlowTemplateConfigService service = new FlowTemplateConfigService(flowTemplateRepository, tenantContext);
            service.deleteFlowTemplate("tpl-1");
            verify(flowTemplateRepository).delete(TENANT_ID, "tpl-1");
        }
    }

    @Nested
    @DisplayName("黑名单 BlacklistConfigService")
    class BlacklistServiceTest {

        private ClaimBlacklistConfigRequest request() {
            ClaimBlacklistConfigRequest request = new ClaimBlacklistConfigRequest();
            request.setSubjectType("PERSON");
            request.setSubjectId("customer-1");
            request.setSubjectName("张三");
            request.setReasonCode("FRAUD_SUSPECTED");
            request.setEffectiveTime(LocalDateTime.now());
            return request;
        }

        @Test
        @DisplayName("blacklistId 空 → 创建即 ACTIVE 并落库")
        void shouldCreateActive() {
            BlacklistConfigService service = new BlacklistConfigService(blacklistRepository, tenantContext);
            String blacklistId = service.saveBlacklist(request());

            assertThat(blacklistId).isNotBlank();
            ArgumentCaptor<ClaimBlacklist> captor = ArgumentCaptor.forClass(ClaimBlacklist.class);
            verify(blacklistRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(BlacklistStatus.ACTIVE);
            assertThat(captor.getValue().getSubjectType()).isEqualTo(BlacklistSubjectType.PERSON);
        }

        @Test
        @DisplayName("撤销：存在 → 保存 REVOKED 状态聚合")
        void shouldRevoke() {
            ClaimBlacklist existing = ClaimBlacklist.create("blk-1", TENANT_ID, BlacklistSubjectType.PERSON,
                    "customer-1", "张三", "FRAUD_SUSPECTED", LocalDateTime.now());
            when(blacklistRepository.findById(TENANT_ID, "blk-1")).thenReturn(Optional.of(existing));

            BlacklistConfigService service = new BlacklistConfigService(blacklistRepository, tenantContext);
            service.revokeBlacklist("blk-1");

            ArgumentCaptor<ClaimBlacklist> captor = ArgumentCaptor.forClass(ClaimBlacklist.class);
            verify(blacklistRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(BlacklistStatus.REVOKED);
        }

        @Test
        @DisplayName("撤销：不存在 → 抛 CLAIM_CONFIG_NOT_FOUND 且不落库")
        void shouldThrowWhenRevokeTargetMissing() {
            when(blacklistRepository.findById(TENANT_ID, "blk-404")).thenReturn(Optional.empty());

            BlacklistConfigService service = new BlacklistConfigService(blacklistRepository, tenantContext);
            assertThatThrownBy(() -> service.revokeBlacklist("blk-404"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("黑名单不存在");
            verify(blacklistRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("医院网络 HospitalNetworkConfigService")
    class HospitalServiceTest {

        private ClaimHospitalNetwork activeHospital() {
            return ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "宠物医院", "三级",
                    HospitalAgreementStatus.ACTIVE, 90, true, "地址", "13800000000");
        }

        @Test
        @DisplayName("暂停：ACTIVE → SUSPENDED 落库")
        void shouldSuspend() {
            when(hospitalRepository.findById(TENANT_ID, "hosp-1")).thenReturn(Optional.of(activeHospital()));

            HospitalNetworkConfigService service =
                    new HospitalNetworkConfigService(hospitalRepository, tenantContext);
            service.suspendHospital("hosp-1");

            ArgumentCaptor<ClaimHospitalNetwork> captor = ArgumentCaptor.forClass(ClaimHospitalNetwork.class);
            verify(hospitalRepository).save(captor.capture());
            assertThat(captor.getValue().getAgreementStatus()).isEqualTo(HospitalAgreementStatus.SUSPENDED);
        }

        @Test
        @DisplayName("恢复：SUSPENDED → ACTIVE 落库")
        void shouldActivate() {
            when(hospitalRepository.findById(TENANT_ID, "hosp-1")).thenReturn(Optional.of(activeHospital()));

            HospitalNetworkConfigService service =
                    new HospitalNetworkConfigService(hospitalRepository, tenantContext);
            service.activateHospital("hosp-1");

            ArgumentCaptor<ClaimHospitalNetwork> captor = ArgumentCaptor.forClass(ClaimHospitalNetwork.class);
            verify(hospitalRepository).save(captor.capture());
            assertThat(captor.getValue().getAgreementStatus()).isEqualTo(HospitalAgreementStatus.ACTIVE);
        }

        @Test
        @DisplayName("行为操作：目标不存在 → 抛 CLAIM_CONFIG_NOT_FOUND")
        void shouldThrowWhenTargetMissing() {
            when(hospitalRepository.findById(eq(TENANT_ID), anyString())).thenReturn(Optional.empty());

            HospitalNetworkConfigService service =
                    new HospitalNetworkConfigService(hospitalRepository, tenantContext);
            assertThatThrownBy(() -> service.terminateHospital("hosp-404"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("医院台账不存在");
        }
    }
}
