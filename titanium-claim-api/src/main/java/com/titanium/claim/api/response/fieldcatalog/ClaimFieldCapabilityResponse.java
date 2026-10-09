package com.titanium.claim.api.response.fieldcatalog;

import io.swagger.v3.oas.annotations.media.Schema;

/** Claim 字段能力响应。 */
@Schema(description = "Claim 字段对取数与分析开放的能力")
public record ClaimFieldCapabilityResponse(
        @Schema(description = "是否允许读取") boolean readable,
        @Schema(description = "是否允许提交变更提案") boolean proposable,
        @Schema(description = "是否允许清空") boolean clearable,
        @Schema(description = "是否已具备真实执行映射") boolean executionSupported,
        @Schema(description = "是否必须指定稳定业务对象ID") boolean requiresObjectId,
        @Schema(description = "业务变更类别编码") String changeTypeCode) {
}
