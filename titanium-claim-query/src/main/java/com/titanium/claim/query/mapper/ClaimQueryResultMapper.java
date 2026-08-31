package com.titanium.claim.query.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.titanium.claim.query.result.ClaimQueryResult;
import com.titanium.claim.query.view.ClaimView;

/**
 * 理赔读模型 → 查询结果映射器（MapStruct）
 * <p>
 * 将读模型实体 {@link ClaimView}（持久化形态）声明式映射为稳定查询契约 {@link ClaimQueryResult}，
 * 取代查询服务中逐字段 set；审计时间戳（基类 createTime/updateTime）映射为结果契约的 createdAt/updatedAt。
 * 禁止直接返回 {@code ClaimView} 泄漏持久化细节。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface ClaimQueryResultMapper {

    /**
     * 理赔读模型 → 查询结果（同名字段自动映射；审计时间戳换名映射）
     */
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    ClaimQueryResult toResult(ClaimView view);
}
