package com.titanium.claim.web.response.config;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 黑名单配置 VO（web 前端出参，理赔配置中心）
 * <p>
 * 由 {@code ClaimConfigWebMapper} 自 {@code ClaimBlacklist} 聚合组装（MapStruct），
 * 标的类型/状态枚举落 code。
 * </p>
 */
@Data
public class ClaimBlacklistConfigVO {
    /** 黑名单ID */
    private String        blacklistId;
    /** 标的类型 code（PERSON/VEHICLE/HOSPITAL/REPAIR_SHOP） */
    private String        subjectType;
    /** 标的主键 */
    private String        subjectId;
    /** 标的名称 */
    private String        subjectName;
    /** 拉黑原因 code */
    private String        reasonCode;
    /** 生效状态 code（ACTIVE/REVOKED） */
    private String        status;
    /** 生效时间 */
    private LocalDateTime effectiveTime;
}
