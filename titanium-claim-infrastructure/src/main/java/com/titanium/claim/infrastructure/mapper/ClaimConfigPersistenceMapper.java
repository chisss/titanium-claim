package com.titanium.claim.infrastructure.mapper;

import java.util.List;
import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.alibaba.fastjson2.JSON;

import com.titanium.claim.aggregate.ClaimBlacklist;
import com.titanium.claim.aggregate.ClaimDocumentTemplate;
import com.titanium.claim.aggregate.ClaimFlowTemplate;
import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.aggregate.ClaimQuickPayRule;
import com.titanium.claim.aggregate.ClaimTimeLimitRule;
import com.titanium.claim.common.enums.config.BlacklistStatus;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.infrastructure.entity.ClaimBlacklistDO;
import com.titanium.claim.infrastructure.entity.ClaimDocumentTemplateDO;
import com.titanium.claim.infrastructure.entity.ClaimFlowTemplateDO;
import com.titanium.claim.infrastructure.entity.ClaimHospitalNetworkDO;
import com.titanium.claim.infrastructure.entity.ClaimPayoutRuleDO;
import com.titanium.claim.infrastructure.entity.ClaimQuickPayRuleDO;
import com.titanium.claim.infrastructure.entity.ClaimTimeLimitRuleDO;

/**
 * 理赔配置子域持久化映射器（MapStruct，聚合 → DO 单向）
 * <p>
 * DO → 聚合方向不走 MapStruct：不可变聚合经 {@code create} 静态工厂重建（构造校验内聚在聚合内，
 * 一次调用非逐字段 set）。聚合 → DO 方向集合字段落库 JSON 文本（fastjson2）、枚举落库 code（红线 20），
 * 经 {@code @Named} 空安全转换方法声明式完成。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimConfigPersistenceMapper {

    // ==================== 流程模板 ====================

    @Mapping(target = "stageSequence", qualifiedByName = "toJsonList")
    @Mapping(target = "stageTimeLimits", source = "stageTimeLimitHours", qualifiedByName = "toJsonMap")
    @Mapping(target = "mandatoryCheckpoints", qualifiedByName = "toJsonList")
    ClaimFlowTemplateDO toDO(ClaimFlowTemplate template);

    // ==================== 赔付规则 ====================

    @Mapping(target = "hospitalTierRatios", qualifiedByName = "toJsonMap")
    @Mapping(target = "exclusions", qualifiedByName = "toJsonList")
    ClaimPayoutRuleDO toDO(ClaimPayoutRule rule);

    // ==================== 快赔规则 ====================

    ClaimQuickPayRuleDO toDO(ClaimQuickPayRule rule);

    // ==================== 单证模板 ====================

    @Mapping(target = "requiredDocuments", qualifiedByName = "toJsonList")
    @Mapping(target = "optionalDocuments", qualifiedByName = "toJsonList")
    ClaimDocumentTemplateDO toDO(ClaimDocumentTemplate template);

    // ==================== 时限规则 ====================

    ClaimTimeLimitRuleDO toDO(ClaimTimeLimitRule rule);

    // ==================== 宠物医院网络 ====================

    @Mapping(target = "agreementStatus", qualifiedByName = "hospitalStatusToCode")
    ClaimHospitalNetworkDO toDO(ClaimHospitalNetwork hospital);

    // ==================== 黑名单 ====================

    @Mapping(target = "subjectType", qualifiedByName = "subjectTypeToCode")
    @Mapping(target = "status", qualifiedByName = "blacklistStatusToCode")
    ClaimBlacklistDO toDO(ClaimBlacklist blacklist);

    // ==================== @Named 空安全转换 ====================

    /** 字符串列表 → JSON 数组文本（空安全） */
    @Named("toJsonList")
    default String toJsonList(List<String> list) {
        return list == null || list.isEmpty() ? null : JSON.toJSONString(list);
    }

    /** 字符串→整数映射 → JSON 对象文本（空安全） */
    @Named("toJsonMap")
    default String toJsonMap(Map<String, Integer> map) {
        return map == null || map.isEmpty() ? null : JSON.toJSONString(map);
    }

    /** 医院协议状态 → code（空安全） */
    @Named("hospitalStatusToCode")
    default String hospitalStatusToCode(HospitalAgreementStatus status) {
        return status == null ? null : status.getCode();
    }

    /** 黑名单标的类型 → code（空安全） */
    @Named("subjectTypeToCode")
    default String subjectTypeToCode(BlacklistSubjectType type) {
        return type == null ? null : type.getCode();
    }

    /** 黑名单状态 → code（空安全） */
    @Named("blacklistStatusToCode")
    default String blacklistStatusToCode(BlacklistStatus status) {
        return status == null ? null : status.getCode();
    }
}
