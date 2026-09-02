package com.titanium.claim.application.model.config;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 黑名单配置入参（application 层配置子域写模型）
 * <p>
 * 新增/更新合一：{@code blacklistId} 为空表示新增（application 雪花生成），非空表示全量更新。
 * 标的类型 code 经 {@code BlacklistSubjectType.fromCode} 还原枚举。
 * </p>
 */
@Data
public class ClaimBlacklistConfigRequest {
    /** 黑名单ID（空=新增） */
    private String        blacklistId;
    /** 标的类型 code（BlacklistSubjectType） */
    private String        subjectType;
    /** 标的主键（人员ID/车牌/医院ID/修理厂ID） */
    private String        subjectId;
    /** 标的名称（展示用） */
    private String        subjectName;
    /** 拉黑原因 code（业务枚举） */
    private String        reasonCode;
    /** 生效时间（空=立即生效） */
    private LocalDateTime effectiveTime;
}
