package com.titanium.claim.api.response.fieldcatalog;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Claim 字段目录响应。 */
@Schema(description = "Claim 权威字段目录")
public record ClaimFieldCatalogResponse(
        @Schema(description = "租户ID") String tenantId,
        @Schema(description = "目录解析业务日期") LocalDate businessDate,
        @Schema(description = "目录版本") String catalogVersion,
        @Schema(description = "目录内容SHA-256") String contentHash,
        @Schema(description = "字段列表") List<ClaimFieldDescriptorResponse> fields) {

    public ClaimFieldCatalogResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
