package com.titanium.claim.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔创建事件
 * <p>
 * {@code tenantId} 随事件流携带，供读模型投影落库（{@code t_claim_view.tenant_id} 非空约束）。
 * 历史事件（该字段引入前产生）反序列化后为 null，投影侧回落默认租户兜底。
 * </p>
 * <p>
 * {@code subjectId}（保单内标的标识）与 {@code vin}（车辆识别代号）为 G11/g11-03（2026-10）
 * 新增的标的键：车险出险时记录被保车辆身份，供 product 域按车辆反算 NCD 时检索历史赔案。
 * 历史事件（两者引入前产生）反序列化后为 null——缺键即该历史赔案不可按车辆检索，属预期而非错误。
 * </p>
 */
@Revision("1")
public record ClaimCreatedEvent(
        ClaimId claimId,
        CustomerId customerId,
        PolicyId policyId,
        String claimNumber,
        ClaimEnum.ClaimType claimType,
        LocalDateTime incidentDate,
        String incidentDescription,
        ClaimAmount claimAmount,
        LocalDateTime createdAt,
        String tenantId,
        String subjectId,
        String vin
) {
}
