package com.titanium.claim.query.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.query.view.ClaimView;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;

/**
 * 理赔读模型投影映射器（MapStruct，事件 → 读模型字段拷贝）
 * <p>
 * 承担「新建型」投影（{@link ClaimCreatedEvent} → {@link ClaimView}）的事件 record → View 字段映射，
 * 取代投影处理器中逐字段 set。采用 {@link MappingTarget} 就地更新既有/新建 View 实例，保留投影 upsert
 * 语义；{@link NullValuePropertyMappingStrategy#IGNORE} 确保事件缺省字段不覆盖 View 既有值。
 * </p>
 * <p>
 * <b>职责边界</b>：仅做纯字段/值对象结构翻译（{@link ClaimId}/{@link CustomerId}/{@link PolicyId} 拆为
 * String、{@link ClaimAmount} 拆为金额）与初始状态常量（status=PENDING、phase=REPORT）。审计时间戳
 * （createTime 仅首次、updateTime 取事件时间）含「仅首次」语义且以事件 {@code createdAt} 为准，仍由投影
 * 处理器控制，不下沉映射器，故此处对应目标字段 {@code ignore}。
 * </p>
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ClaimViewMapper {

    /** 理赔创建事件 → 理赔读模型（就地 upsert；值对象拆解 + 初始状态常量；审计时间戳留在处理器） */
    @Mapping(target = "claimId", source = "claimId", qualifiedByName = "claimIdValue")
    @Mapping(target = "customerId", source = "customerId", qualifiedByName = "customerIdValue")
    @Mapping(target = "policyId", source = "policyId", qualifiedByName = "policyIdValue")
    @Mapping(target = "claimAmount", source = "claimAmount", qualifiedByName = "claimAmountValue")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "phase", constant = "REPORT")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyCreated(@MappingTarget ClaimView view, ClaimCreatedEvent event);

    /** ClaimId → 字符串标识（空安全） */
    @Named("claimIdValue")
    default String claimIdValue(ClaimId claimId) {
        return claimId != null ? claimId.value() : null;
    }

    /** CustomerId → 字符串标识（空安全） */
    @Named("customerIdValue")
    default String customerIdValue(CustomerId customerId) {
        return customerId != null ? customerId.value() : null;
    }

    /** PolicyId → 字符串标识（空安全） */
    @Named("policyIdValue")
    default String policyIdValue(PolicyId policyId) {
        return policyId != null ? policyId.value() : null;
    }

    /** ClaimAmount → 金额数值（空安全） */
    @Named("claimAmountValue")
    default BigDecimal claimAmountValue(ClaimAmount claimAmount) {
        return claimAmount != null ? claimAmount.value() : null;
    }
}
