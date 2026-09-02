package com.titanium.claim.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.titanium.claim.aggregate.ClaimBlacklist;
import com.titanium.claim.aggregate.ClaimDocumentTemplate;
import com.titanium.claim.aggregate.ClaimFlowTemplate;
import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.aggregate.ClaimPayoutRule;
import com.titanium.claim.aggregate.ClaimTimeLimitRule;
import com.titanium.claim.application.model.config.ClaimBlacklistConfigRequest;
import com.titanium.claim.application.model.config.ClaimDocumentTemplateConfigRequest;
import com.titanium.claim.application.model.config.ClaimFlowTemplateConfigRequest;
import com.titanium.claim.application.model.config.ClaimHospitalNetworkConfigRequest;
import com.titanium.claim.application.model.config.ClaimPayoutRuleConfigRequest;
import com.titanium.claim.application.model.config.ClaimTimeLimitRuleConfigRequest;
import com.titanium.claim.common.enums.config.BlacklistStatus;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.web.dto.config.ClaimBlacklistConfigDTO;
import com.titanium.claim.web.dto.config.ClaimDocumentTemplateConfigDTO;
import com.titanium.claim.web.dto.config.ClaimFlowTemplateConfigDTO;
import com.titanium.claim.web.dto.config.ClaimHospitalNetworkConfigDTO;
import com.titanium.claim.web.dto.config.ClaimPayoutRuleConfigDTO;
import com.titanium.claim.web.dto.config.ClaimTimeLimitRuleConfigDTO;
import com.titanium.claim.web.response.config.ClaimBlacklistConfigVO;
import com.titanium.claim.web.response.config.ClaimDocumentTemplateConfigVO;
import com.titanium.claim.web.response.config.ClaimFlowTemplateConfigVO;
import com.titanium.claim.web.response.config.ClaimHospitalNetworkConfigVO;
import com.titanium.claim.web.response.config.ClaimPayoutRuleConfigVO;
import com.titanium.claim.web.response.config.ClaimTimeLimitRuleConfigVO;

/**
 * 理赔配置中心 Web 层对象映射器（MapStruct）
 * <p>
 * 承接 {@code ClaimConfigController} 的边界协议转换：前端 {@code XxxDTO}（web/dto/config）→ 应用层配置写模型
 * （{@code XxxRequest}，同名字段自动映射）；配置子域聚合根 → 展示 {@code XxxVO}（web/response/config），
 * 协议状态/标的类型/生效状态枚举经空安全 {@code @Named} 方法落 code，租户维度不出 web。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimConfigWebMapper {

    // ==================== DTO → 应用层配置写模型 ====================

    /** 流程模板 DTO → 应用层入参 */
    ClaimFlowTemplateConfigRequest toRequest(ClaimFlowTemplateConfigDTO dto);

    /** 赔付规则 DTO → 应用层入参 */
    ClaimPayoutRuleConfigRequest toRequest(ClaimPayoutRuleConfigDTO dto);

    /** 单证模板 DTO → 应用层入参 */
    ClaimDocumentTemplateConfigRequest toRequest(ClaimDocumentTemplateConfigDTO dto);

    /** 时限规则 DTO → 应用层入参 */
    ClaimTimeLimitRuleConfigRequest toRequest(ClaimTimeLimitRuleConfigDTO dto);

    /** 医院网络 DTO → 应用层入参 */
    ClaimHospitalNetworkConfigRequest toRequest(ClaimHospitalNetworkConfigDTO dto);

    /** 黑名单 DTO → 应用层入参 */
    ClaimBlacklistConfigRequest toRequest(ClaimBlacklistConfigDTO dto);

    // ==================== 聚合根 → 展示 VO ====================

    /** 流程模板聚合 → VO */
    ClaimFlowTemplateConfigVO toVO(ClaimFlowTemplate template);

    /** 赔付规则聚合 → VO */
    ClaimPayoutRuleConfigVO toVO(ClaimPayoutRule rule);

    /** 单证模板聚合 → VO */
    ClaimDocumentTemplateConfigVO toVO(ClaimDocumentTemplate template);

    /** 时限规则聚合 → VO */
    ClaimTimeLimitRuleConfigVO toVO(ClaimTimeLimitRule rule);

    /** 医院网络聚合 → VO（协议状态枚举落 code） */
    @Mapping(target = "agreementStatus", source = "agreementStatus", qualifiedByName = "hospitalStatusToCode")
    ClaimHospitalNetworkConfigVO toVO(ClaimHospitalNetwork hospital);

    /** 黑名单聚合 → VO（标的类型/生效状态枚举落 code） */
    @Mapping(target = "subjectType", source = "subjectType", qualifiedByName = "subjectTypeToCode")
    @Mapping(target = "status", source = "status", qualifiedByName = "blacklistStatusToCode")
    ClaimBlacklistConfigVO toVO(ClaimBlacklist blacklist);

    // ==================== 枚举 → code 转换辅助（空安全） ====================

    /**
     * 医院协议状态枚举 → code（空安全）
     */
    @Named("hospitalStatusToCode")
    default String hospitalStatusToCode(HospitalAgreementStatus status) {
        return status != null ? status.getCode() : null;
    }

    /**
     * 黑名单标的类型枚举 → code（空安全）
     */
    @Named("subjectTypeToCode")
    default String subjectTypeToCode(BlacklistSubjectType subjectType) {
        return subjectType != null ? subjectType.getCode() : null;
    }

    /**
     * 黑名单生效状态枚举 → code（空安全）
     */
    @Named("blacklistStatusToCode")
    default String blacklistStatusToCode(BlacklistStatus status) {
        return status != null ? status.getCode() : null;
    }
}
