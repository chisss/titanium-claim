package com.titanium.claim.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.titanium.claim.common.enums.config.BlacklistStatus;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.exception.BusinessException;

/**
 * 理赔配置子域聚合不变量测试（流程模板/赔付规则/单证模板/时限规则/医院网络/黑名单）
 * <p>
 * 六聚合均为状态存储不可变聚合（create 静态工厂 + 行为方法返回新实例），校验内聚于私有构造器，
 * 违反不变量抛 {@code BusinessException(CLAIM_CONFIG_INVALID)}。本测试覆盖各聚合的非法输入拦截与
 * 合法行为流转（医院协议状态机、黑名单撤销与命中判定）。
 * </p>
 */
@DisplayName("理赔配置子域聚合不变量")
class ClaimConfigAggregateTest {

    private static final String TENANT_ID = "tenant-001";

    @Nested
    @DisplayName("流程模板 ClaimFlowTemplate")
    class FlowTemplateTest {

        @Test
        @DisplayName("环节序列为空时拒绝创建")
        void shouldRejectWhenStageSequenceEmpty() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimFlowTemplate.create("tpl-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                            List.of(), null, "理赔专员", null));
            assertTrue(ex.getMessage().contains("环节序列不能为空"));
        }

        @Test
        @DisplayName("环节时限未落在环节序列内时拒绝创建")
        void shouldRejectWhenTimeLimitStageNotInSequence() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimFlowTemplate.create("tpl-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                            List.of("报案", "核赔"), Map.of("查勘", 24), "理赔专员", null));
            assertTrue(ex.getMessage().contains("环节时限"));
        }

        @Test
        @DisplayName("必经校验点未落在环节序列内时拒绝创建")
        void shouldRejectWhenCheckpointNotInSequence() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimFlowTemplate.create("tpl-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                            List.of("报案", "核赔"), null, "理赔专员", List.of("给付")));
            assertTrue(ex.getMessage().contains("必经校验点"));
        }

        @Test
        @DisplayName("合法输入创建成功且 update 全量覆盖")
        void shouldCreateAndUpdate() {
            ClaimFlowTemplate template = ClaimFlowTemplate.create("tpl-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                    List.of("报案", "资料审核", "核赔", "给付"), Map.of("报案", 1, "核赔", 48), "理赔专员",
                    List.of("资料审核"));
            assertEquals(List.of("报案", "资料审核", "核赔", "给付"), template.getStageSequence());
            assertEquals(Integer.valueOf(48), template.getStageTimeLimitHours().get("核赔"));

            ClaimFlowTemplate updated = template.update("MEDICAL", "MEDICAL_REIMBURSE",
                    List.of("报案", "核赔"), Map.of("核赔", 24), "核赔人", null);
            assertEquals("tpl-1", updated.getTemplateId());
            assertTrue(updated.getMandatoryCheckpoints().isEmpty());
        }
    }

    @Nested
    @DisplayName("赔付规则 ClaimPayoutRule")
    class PayoutRuleTest {

        @Test
        @DisplayName("赔付比例越界时拒绝创建")
        void shouldRejectWhenPayoutRatioOutOfRange() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimPayoutRule.create("rule-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                            BigDecimal.ZERO, 120, null, null, null, null));
            assertTrue(ex.getMessage().contains("赔付比例"));
        }

        @Test
        @DisplayName("免赔额大于单次限额时拒绝创建")
        void shouldRejectWhenDeductibleExceedsPerClaimLimit() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimPayoutRule.create("rule-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                            new BigDecimal("500.00"), 80, new BigDecimal("300.00"), null, null, null));
            assertTrue(ex.getMessage().contains("免赔额"));
        }

        @Test
        @DisplayName("医院分档比例越界时拒绝创建")
        void shouldRejectWhenTierRatioOutOfRange() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimPayoutRule.create("rule-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                            BigDecimal.ZERO, 80, null, null, Map.of("一级", 110), null));
            assertTrue(ex.getMessage().contains("医院分档比例"));
        }

        @Test
        @DisplayName("合法输入创建成功")
        void shouldCreateWhenValid() {
            ClaimPayoutRule rule = ClaimPayoutRule.create("rule-1", TENANT_ID, "PET", "PET_MEDICAL",
                    new BigDecimal("200.00"), 70, new BigDecimal("3000.00"), new BigDecimal("10000.00"),
                    Map.of("一级", 80, "二级", 60), List.of("美容费用"));
            assertEquals(Integer.valueOf(70), rule.getPayoutRatio());
            assertEquals(Integer.valueOf(60), rule.getHospitalTierRatios().get("二级"));
        }
    }

    @Nested
    @DisplayName("单证模板 ClaimDocumentTemplate")
    class DocumentTemplateTest {

        @Test
        @DisplayName("必填与选填材料重复时拒绝创建")
        void shouldRejectWhenDocumentsOverlap() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimDocumentTemplate.create("tpl-1", TENANT_ID, "MEDICAL", "MEDICAL_REIMBURSE",
                            List.of("发票", "病历"), List.of("病历")));
            assertTrue(ex.getMessage().contains("不能重复"));
        }

        @Test
        @DisplayName("合法输入创建成功")
        void shouldCreateWhenValid() {
            ClaimDocumentTemplate template = ClaimDocumentTemplate.create("tpl-1", TENANT_ID, "PET", "PET_MEDICAL",
                    List.of("发票", "病历"), List.of("宠物照片"));
            assertEquals(List.of("发票", "病历"), template.getRequiredDocuments());
        }
    }

    @Nested
    @DisplayName("时限规则 ClaimTimeLimitRule")
    class TimeLimitRuleTest {

        @Test
        @DisplayName("环节时限非正数时拒绝创建")
        void shouldRejectWhenLimitHoursNotPositive() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimTimeLimitRule.create("rule-1", TENANT_ID, "MEDICAL", "资料审核", 0, 1));
            assertTrue(ex.getMessage().contains("环节时限"));
        }

        @Test
        @DisplayName("预警时限不小于环节时限时拒绝创建")
        void shouldRejectWhenAlertHoursExceedsLimit() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimTimeLimitRule.create("rule-1", TENANT_ID, "MEDICAL", "资料审核", 48, 48));
            assertTrue(ex.getMessage().contains("预警时限"));
        }

        @Test
        @DisplayName("合法输入创建成功")
        void shouldCreateWhenValid() {
            ClaimTimeLimitRule rule = ClaimTimeLimitRule.create("rule-1", TENANT_ID, "MEDICAL",
                    "资料审核", 48, 24);
            assertEquals(Integer.valueOf(48), rule.getLimitHours());
            assertEquals(Integer.valueOf(24), rule.getAlertHours());
        }
    }

    @Nested
    @DisplayName("医院网络 ClaimHospitalNetwork")
    class HospitalNetworkTest {

        @Test
        @DisplayName("医院名称为空时拒绝创建")
        void shouldRejectWhenNameBlank() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimHospitalNetwork.create("hosp-1", TENANT_ID, " ",
                            "三级", HospitalAgreementStatus.ACTIVE, 90, true, "地址", "13800000000"));
            assertTrue(ex.getMessage().contains("医院名称"));
        }

        @Test
        @DisplayName("定点赔付比例越界时拒绝创建")
        void shouldRejectWhenPayoutRatioOutOfRange() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "宠物医院",
                            "三级", HospitalAgreementStatus.ACTIVE, -1, true, "地址", "13800000000"));
            assertTrue(ex.getMessage().contains("定点赔付比例"));
        }

        @Test
        @DisplayName("协议状态流转：暂停/恢复/终止")
        void shouldTransitionAgreementStatus() {
            ClaimHospitalNetwork hospital = ClaimHospitalNetwork.create("hosp-1", TENANT_ID, "宠物医院",
                    "三级", HospitalAgreementStatus.ACTIVE, 90, true, "地址", "13800000000");
            assertTrue(hospital.isEligible());

            ClaimHospitalNetwork suspended = hospital.suspend();
            assertEquals(HospitalAgreementStatus.SUSPENDED, suspended.getAgreementStatus());
            assertFalse(suspended.isEligible());

            assertEquals(HospitalAgreementStatus.ACTIVE, suspended.activate().getAgreementStatus());
            assertEquals(HospitalAgreementStatus.TERMINATED, hospital.terminate().getAgreementStatus());
            assertFalse(hospital.terminate().isEligible());
        }
    }

    @Nested
    @DisplayName("黑名单 ClaimBlacklist")
    class BlacklistTest {

        private ClaimBlacklist activeBlacklist() {
            return ClaimBlacklist.create("blk-1", TENANT_ID, BlacklistSubjectType.PERSON,
                    "customer-1", "张三", "FRAUD_SUSPECTED", LocalDateTime.now());
        }

        @Test
        @DisplayName("标的ID为空时拒绝创建")
        void shouldRejectWhenSubjectIdBlank() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> ClaimBlacklist.create("blk-1", TENANT_ID, BlacklistSubjectType.PERSON,
                            " ", "张三", "FRAUD_SUSPECTED", LocalDateTime.now()));
            assertTrue(ex.getMessage().contains("标的ID"));
        }

        @Test
        @DisplayName("创建即生效（ACTIVE）且命中判定为真")
        void shouldCreateActiveAndHit() {
            ClaimBlacklist blacklist = activeBlacklist();
            assertEquals(BlacklistStatus.ACTIVE, blacklist.getStatus());
            assertTrue(blacklist.isHit());
        }

        @Test
        @DisplayName("update 保持生效状态不变")
        void shouldKeepStatusOnUpdate() {
            ClaimBlacklist updated = activeBlacklist().update("PERSON", "customer-1", "张三",
                    "OTHER_REASON", LocalDateTime.now());
            assertEquals(BlacklistStatus.ACTIVE, updated.getStatus());
            assertEquals("OTHER_REASON", updated.getReasonCode());
        }

        @Test
        @DisplayName("撤销后命中判定为假")
        void shouldMissAfterRevoke() {
            ClaimBlacklist revoked = activeBlacklist().revoke();
            assertEquals(BlacklistStatus.REVOKED, revoked.getStatus());
            assertFalse(revoked.isHit());
        }
    }
}
