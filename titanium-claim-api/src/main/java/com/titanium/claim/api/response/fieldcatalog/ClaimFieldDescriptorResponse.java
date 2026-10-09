package com.titanium.claim.api.response.fieldcatalog;

import java.time.LocalDate;

import com.titanium.metadata.enums.claim.fieldcatalog.ClaimFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

import io.swagger.v3.oas.annotations.media.Schema;

/** Claim 稳定字段描述响应。 */
@Schema(description = "Claim 稳定字段描述")
public record ClaimFieldDescriptorResponse(
        @Schema(description = "稳定字段编码") String fieldCode,
        @Schema(description = "业务对象类型") ClaimFieldObjectType objectType,
        @Schema(description = "字段值类型") PolicyFieldValueType valueType,
        @Schema(description = "国际化标签键") String labelKey,
        @Schema(description = "是否属于集合对象") boolean collection,
        @Schema(description = "集合对象稳定标识字段") String objectIdentityField,
        @Schema(description = "字段能力") ClaimFieldCapabilityResponse capability,
        @Schema(description = "敏感级别") PolicyFieldSensitivityLevel sensitivity,
        @Schema(description = "掩码策略") PolicyFieldMaskingPolicy maskingPolicy,
        @Schema(description = "废弃日期") LocalDate deprecatedAt,
        @Schema(description = "分析能力位（跨域共享；非 null，反序列化缺字段会暴露为契约漂移）")
        FieldAnalysisCapability analysis) {
}
