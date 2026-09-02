package com.titanium.claim.web.dto.config;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 黑名单配置 DTO（web 前端入参，理赔配置中心）
 * <p>
 * 新增/更新合一：{@code blacklistId} 空=新增，非空=全量更新。经 {@code ClaimConfigWebMapper}
 * 翻译为应用层配置写模型。
 * </p>
 */
@Data
public class ClaimBlacklistConfigDTO {
    /** 黑名单ID（空=新增） */
    private String        blacklistId;
    /** 标的类型 code（PERSON/VEHICLE/HOSPITAL/REPAIR_SHOP） */
    @NotBlank(message = "标的类型不能为空")
    private String        subjectType;
    /** 标的主键（人员ID/车牌/医院ID/修理厂ID） */
    @NotBlank(message = "标的ID不能为空")
    private String        subjectId;
    /** 标的名称（展示用） */
    private String        subjectName;
    /** 拉黑原因 code */
    @NotBlank(message = "拉黑原因不能为空")
    private String        reasonCode;
    /** 生效时间（空=立即生效） */
    private LocalDateTime effectiveTime;
}
