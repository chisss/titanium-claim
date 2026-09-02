package com.titanium.claim.aggregate;

import java.util.List;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 单证模板聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 按「险种线 × 理赔类型」配置必填/选填材料清单（健康险/宠物险单证审核链路核心，
 * 产品文档 §2.8），是产品 {@code ClaimConfig.requiredDocuments} 的结构化运营版。
 * </p>
 */
@Getter
public final class ClaimDocumentTemplate {

    private final String       templateId;
    private final String       tenantId;
    /** 险种线（metadata InsuranceProductType code） */
    private final String       insuranceLine;
    /** 理赔类型（metadata ClaimEnum.ClaimType code） */
    private final String       claimType;
    /** 必填材料清单（如 病历/出院小结/发票） */
    private final List<String> requiredDocuments;
    /** 选填材料清单 */
    private final List<String> optionalDocuments;

    private ClaimDocumentTemplate(String templateId, String tenantId, String insuranceLine, String claimType,
                                  List<String> requiredDocuments, List<String> optionalDocuments) {
        if (requiredDocuments != null && optionalDocuments != null
                && requiredDocuments.stream().anyMatch(optionalDocuments::contains)) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "必填与选填材料清单不能重复");
        }
        this.templateId = templateId;
        this.tenantId = tenantId;
        this.insuranceLine = insuranceLine;
        this.claimType = claimType;
        this.requiredDocuments = requiredDocuments == null ? List.of() : List.copyOf(requiredDocuments);
        this.optionalDocuments = optionalDocuments == null ? List.of() : List.copyOf(optionalDocuments);
    }

    /**
     * 创建单证模板
     *
     * @param templateId        模板ID（application 雪花生成）
     * @param tenantId          租户ID（平台默认 'platform'）
     * @param insuranceLine     险种线 code
     * @param claimType         理赔类型 code
     * @param requiredDocuments 必填材料清单
     * @param optionalDocuments 选填材料清单
     * @return 单证模板聚合
     */
    public static ClaimDocumentTemplate create(String templateId, String tenantId, String insuranceLine,
                                               String claimType, List<String> requiredDocuments,
                                               List<String> optionalDocuments) {
        return new ClaimDocumentTemplate(templateId, tenantId, insuranceLine, claimType,
                requiredDocuments, optionalDocuments);
    }

    /**
     * 全量更新单证模板（后台表单全量提交，返回新实例）
     *
     * @return 更新后的单证模板聚合
     */
    public ClaimDocumentTemplate update(String insuranceLine, String claimType,
                                        List<String> requiredDocuments, List<String> optionalDocuments) {
        return new ClaimDocumentTemplate(templateId, tenantId, insuranceLine, claimType,
                requiredDocuments, optionalDocuments);
    }
}
