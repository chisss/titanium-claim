package com.titanium.claim.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.titanium.claim.api.request.ClaimRequest;
import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.claim.application.model.assessment.SubmitLossAssessmentRequest;
import com.titanium.claim.application.model.assessment.SubmitSurveyRequest;
import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.application.model.maintenance.UpdateClaimRequest;
import com.titanium.claim.application.model.settlement.SettleClaimRequest;
import com.titanium.claim.application.model.settlement.SettleDeathBenefitRequest;
import com.titanium.claim.application.query.ClaimReadModel;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.web.dto.CreateClaimDTO;
import com.titanium.claim.web.dto.SettleClaimDTO;
import com.titanium.claim.web.dto.SettleDeathBenefitDTO;
import com.titanium.claim.web.dto.SubmitLossAssessmentDTO;
import com.titanium.claim.web.dto.SubmitSurveyDTO;
import com.titanium.claim.web.dto.UpdateClaimDTO;
import com.titanium.claim.web.response.ClaimResponseVO;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔案件 Web 层对象映射器（MapStruct）
 * <p>
 * 边界协议转换枢纽：对内 {@code ClaimController} 把后台/端上 {@code XxxDTO}（web/dto）翻译为应用层入参模型
 * （{@code XxxRequest}）；对外 {@code ClaimApiProvider} 把远程契约 {@code XxxRequest}（api/request）翻译为应用层
 * 入参模型；应用层读模型 {@link ClaimReadModel} 再分别组装为展示 {@link ClaimResponseVO}（Controller）与对外
 * {@link ClaimResponse}（Provider）。应用层做真正的编排（生成 ClaimId/理赔编号、跨域校验保单），故写门面
 * 入参为应用层模型，本映射器只承担 DTO/Request ⇄ 应用层模型的结构翻译（同名字段自动映射，枚举 code
 * 经空安全 {@code @Named} 方法还原）。api 层请求类型与应用层同名，Provider 方法以全限定名区分。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimWebMapper {

    // ==================== Controller：Web DTO → 应用层入参模型 ====================

    /**
     * 后台创建 DTO → 应用层创建入参
     */
    CreateClaimRequest toCreateRequest(CreateClaimDTO dto);

    /**
     * 后台更新 DTO → 应用层更新入参
     */
    UpdateClaimRequest toUpdateRequest(UpdateClaimDTO dto);

    /**
     * 后台查勘 DTO → 应用层查勘入参
     */
    SubmitSurveyRequest toSurveyRequest(SubmitSurveyDTO dto);

    /**
     * 后台定损 DTO → 应用层定损入参
     */
    SubmitLossAssessmentRequest toLossAssessmentRequest(SubmitLossAssessmentDTO dto);

    /**
     * 后台结算 DTO → 应用层结算入参
     */
    SettleClaimRequest toSettleRequest(SettleClaimDTO dto);

    /**
     * 后台身故给付结算 DTO → 应用层身故给付结算入参（寿险专属）
     */
    SettleDeathBenefitRequest toDeathBenefitRequest(SettleDeathBenefitDTO dto);

    /**
     * 应用层读模型 → 展示 VO（Controller 用，状态/理赔类型 code 还原为枚举）
     */
    @Mapping(target = "status", source = "status", qualifiedByName = "toStatus")
    @Mapping(target = "claimType", source = "claimType", qualifiedByName = "toClaimType")
    ClaimResponseVO toVO(ClaimReadModel readModel);

    // ==================== Provider：api Request → 应用层入参模型 ====================

    /**
     * 对外创建请求 → 应用层创建入参
     */
    CreateClaimRequest toCreateRequest(ClaimRequest request);

    /**
     * 对外更新请求 → 应用层更新入参
     */
    UpdateClaimRequest toUpdateRequest(ClaimRequest request);

    /**
     * 对外查勘请求 → 应用层查勘入参
     */
    SubmitSurveyRequest toSurveyRequest(com.titanium.claim.api.request.SubmitSurveyRequest request);

    /**
     * 对外定损请求 → 应用层定损入参
     */
    SubmitLossAssessmentRequest toLossAssessmentRequest(
            com.titanium.claim.api.request.SubmitLossAssessmentRequest request);

    /**
     * 对外结算请求 → 应用层结算入参
     */
    SettleClaimRequest toSettleRequest(com.titanium.claim.api.request.SettleClaimRequest request);

    /**
     * 应用层读模型 → 对外响应（Provider 用，同名字段结构映射）
     */
    ClaimResponse toApiResponse(ClaimReadModel readModel);

    // ==================== 类型转换辅助（空安全） ====================

    /**
     * 状态码 → 理赔状态枚举（空安全）
     */
    @Named("toStatus")
    default ClaimStatus toStatus(String code) {
        return code != null ? ClaimStatus.fromCode(code) : null;
    }

    /**
     * 理赔类型码 → 理赔类型枚举（空安全）
     */
    @Named("toClaimType")
    default ClaimEnum.ClaimType toClaimType(String code) {
        return code != null ? ClaimEnum.ClaimType.fromCode(code) : null;
    }
}
