package com.titanium.claim.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.claim.api.response.ClaimStatisticsResponse;
import com.titanium.claim.query.result.ClaimStatisticsResult;

/**
 * 理赔统计 Web 层对象映射器（MapStruct）
 * <p>
 * 将读侧统计结果 {@link ClaimStatisticsResult} 声明式映射为对外远程契约 {@link ClaimStatisticsResponse}。
 * 字段同名同类型，由 MapStruct 自动映射，无需手工 set。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimStatisticsWebMapper {

    /** 读侧统计结果 → 对外统计响应（字段同名自动映射） */
    ClaimStatisticsResponse toResponse(ClaimStatisticsResult result);
}
