package com.titanium.claim.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.titanium.claim.api.dto.ClaimRequestDTO;
import com.titanium.claim.api.dto.ClaimResponseDTO;
import com.titanium.claim.api.dto.SettleClaimRequestDTO;
import com.titanium.claim.api.dto.SubmitLossAssessmentRequestDTO;
import com.titanium.claim.api.dto.SubmitSurveyRequestDTO;
import com.titanium.claim.application.dto.CreateClaimRequestDTO;
import com.titanium.claim.application.dto.UpdateClaimRequestDTO;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.web.request.CreateClaimRequestVO;
import com.titanium.claim.web.request.SettleClaimRequestVO;
import com.titanium.claim.web.request.SubmitLossAssessmentRequestVO;
import com.titanium.claim.web.request.SubmitSurveyRequestVO;
import com.titanium.claim.web.request.UpdateClaimRequestVO;
import com.titanium.claim.web.response.ClaimResponseVO;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔案件 Web 层对象映射器（MapStruct）
 * <p>
 * 边界协议转换枢纽：对内 {@code ClaimController} 把后台/端上 {@code XxxRequestVO} 翻译为应用层入参
 * DTO；对外 {@code ClaimApiProvider} 把远程契约 {@code XxxRequestDTO}（api）翻译为应用层入参 DTO；
 * 应用层响应 {@code ClaimResponseDTO} 再分别组装为展示 {@code ClaimResponseVO}（Controller）与对外
 * {@code ClaimResponseDTO}（Provider）。应用层做真正的编排（生成 ClaimId/理赔编号、跨域校验保单），
 * 故写门面入参保留应用层 DTO，本映射器只承担 Request/DTO ⇄ 应用层 DTO 的结构翻译。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimWebMapper {

    // ==================== Controller：Web Request VO → 应用层 DTO ====================

    /**
     * 后台创建请求 VO → 应用层创建入参 DTO
     */
    CreateClaimRequestDTO toCreateDTO(CreateClaimRequestVO requestVO);

    /**
     * 后台更新请求 VO → 应用层更新入参 DTO
     */
    UpdateClaimRequestDTO toUpdateDTO(UpdateClaimRequestVO requestVO);

    /**
     * 后台查勘请求 VO → 应用层查勘入参 DTO
     */
    com.titanium.claim.application.dto.SubmitSurveyRequestDTO toSurveyDTO(SubmitSurveyRequestVO requestVO);

    /**
     * 后台定损请求 VO → 应用层定损入参 DTO
     */
    com.titanium.claim.application.dto.SubmitLossAssessmentRequestDTO toLossAssessmentDTO(
            SubmitLossAssessmentRequestVO requestVO);

    /**
     * 后台结算请求 VO → 应用层结算入参 DTO
     */
    com.titanium.claim.application.dto.SettleClaimRequestDTO toSettleDTO(SettleClaimRequestVO requestVO);

    /**
     * 应用层响应 DTO → 展示 VO（Controller 用，状态/理赔类型 code 还原为枚举）
     */
    @Mapping(target = "status", expression = "java(toStatus(responseDTO.getStatus()))")
    @Mapping(target = "claimType", expression = "java(toClaimType(responseDTO.getClaimType()))")
    ClaimResponseVO toVO(com.titanium.claim.application.dto.ClaimResponseDTO responseDTO);

    // ==================== Provider：api DTO → 应用层 DTO ====================

    /**
     * 对外创建请求 DTO → 应用层创建入参 DTO
     */
    CreateClaimRequestDTO toCreateDTO(ClaimRequestDTO requestDTO);

    /**
     * 对外更新请求 DTO → 应用层更新入参 DTO
     */
    UpdateClaimRequestDTO toUpdateDTO(ClaimRequestDTO requestDTO);

    /**
     * 对外查勘请求 DTO → 应用层查勘入参 DTO
     */
    com.titanium.claim.application.dto.SubmitSurveyRequestDTO toSurveyDTO(SubmitSurveyRequestDTO requestDTO);

    /**
     * 对外定损请求 DTO → 应用层定损入参 DTO
     */
    com.titanium.claim.application.dto.SubmitLossAssessmentRequestDTO toLossAssessmentDTO(
            SubmitLossAssessmentRequestDTO requestDTO);

    /**
     * 对外结算请求 DTO → 应用层结算入参 DTO
     */
    com.titanium.claim.application.dto.SettleClaimRequestDTO toSettleDTO(SettleClaimRequestDTO requestDTO);

    /**
     * 应用层响应 DTO → 对外响应 DTO（Provider 用）
     */
    ClaimResponseDTO toApiResponse(com.titanium.claim.application.dto.ClaimResponseDTO responseDTO);

    // ==================== 类型转换辅助（空安全） ====================

    /**
     * 状态码 → 理赔状态枚举（空安全）
     */
    default ClaimStatus toStatus(String code) {
        return code != null ? ClaimStatus.fromCode(code) : null;
    }

    /**
     * 理赔类型码 → 理赔类型枚举（空安全）
     */
    default ClaimEnum.ClaimType toClaimType(String code) {
        return code != null ? ClaimEnum.ClaimType.fromCode(code) : null;
    }
}
