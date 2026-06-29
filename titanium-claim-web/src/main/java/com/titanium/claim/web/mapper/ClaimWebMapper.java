package com.titanium.claim.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.claim.api.dto.ClaimRequestDTO;
import com.titanium.claim.api.dto.ClaimResponseDTO;
import com.titanium.claim.application.dto.CreateClaimRequestDTO;
import com.titanium.claim.application.dto.UpdateClaimRequestDTO;

/**
 * 理赔案件 Web 映射器
 * <p>
 * 用于在 API 层 DTO（对外契约）与 Application 层 DTO（命令/查询入参与出参）之间转换。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimWebMapper {

    /**
     * 将对外创建请求 DTO 转换为应用层创建命令 DTO
     */
    CreateClaimRequestDTO toCreateDTO(ClaimRequestDTO requestDTO);

    /**
     * 将对外更新请求 DTO 转换为应用层更新命令 DTO
     */
    UpdateClaimRequestDTO toUpdateDTO(ClaimRequestDTO requestDTO);

    /**
     * 将应用层响应 DTO 转换为对外响应 DTO
     */
    ClaimResponseDTO toApiResponse(com.titanium.claim.application.dto.ClaimResponseDTO responseDTO);
}
