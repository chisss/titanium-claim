package com.titanium.claim.aggregate;

import java.time.LocalDateTime;

import com.titanium.claim.common.enums.config.BlacklistStatus;
import com.titanium.claim.common.enums.config.BlacklistSubjectType;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 黑名单聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 人/车/医院/修理厂黑名单台账与命中提示数据源（产品文档 §4.2，反欺诈联防 P2）。
 * 仅 {@code ACTIVE} 态参与报案侧/理算侧命中提示（dev-012 反欺诈警示消费）。
 * </p>
 */
@Getter
public final class ClaimBlacklist {

    private final String               blacklistId;
    private final String               tenantId;
    /** 标的类型（PERSON/VEHICLE/HOSPITAL/REPAIR_SHOP，持久化 code） */
    private final BlacklistSubjectType subjectType;
    /** 标的主键（人员ID/车牌/医院ID/修理厂ID） */
    private final String               subjectId;
    /** 标的名称（展示用） */
    private final String               subjectName;
    /** 拉黑原因 code（业务枚举，落库 code 不落中文文案，红线 20） */
    private final String               reasonCode;
    /** 生效状态（ACTIVE/REVOKED，持久化 code） */
    private final BlacklistStatus      status;
    /** 生效时间 */
    private final LocalDateTime        effectiveTime;

    private ClaimBlacklist(String blacklistId, String tenantId, BlacklistSubjectType subjectType,
                           String subjectId, String subjectName, String reasonCode,
                           BlacklistStatus status, LocalDateTime effectiveTime) {
        if (subjectType == null) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "黑名单标的类型不能为空");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "黑名单标的ID不能为空");
        }
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "拉黑原因不能为空");
        }
        this.blacklistId = blacklistId;
        this.tenantId = tenantId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.reasonCode = reasonCode;
        this.status = status == null ? BlacklistStatus.ACTIVE : status;
        this.effectiveTime = effectiveTime == null ? LocalDateTime.now() : effectiveTime;
    }

    /**
     * 创建黑名单条目（默认立即生效）
     *
     * @param blacklistId   黑名单ID（application 雪花生成）
     * @param tenantId      租户ID（平台默认 'platform'）
     * @param subjectType   标的类型
     * @param subjectId     标的主键
     * @param subjectName   标的名称
     * @param reasonCode    拉黑原因 code
     * @param effectiveTime 生效时间（可为空，默认当前时间）
     * @return 黑名单聚合
     */
    public static ClaimBlacklist create(String blacklistId, String tenantId, BlacklistSubjectType subjectType,
                                        String subjectId, String subjectName, String reasonCode,
                                        LocalDateTime effectiveTime) {
        return new ClaimBlacklist(blacklistId, tenantId, subjectType, subjectId, subjectName,
                reasonCode, BlacklistStatus.ACTIVE, effectiveTime);
    }

    /**
     * 全量更新黑名单条目（后台表单全量提交，返回新实例，状态保持不变）
     *
     * @return 更新后的黑名单聚合
     */
    public ClaimBlacklist update(String subjectType, String subjectId, String subjectName,
                                 String reasonCode, LocalDateTime effectiveTime) {
        return new ClaimBlacklist(blacklistId, tenantId, BlacklistSubjectType.fromCode(subjectType),
                subjectId, subjectName, reasonCode, status, effectiveTime);
    }

    /**
     * 撤销黑名单：留存记录，不再参与命中提示
     *
     * @return 撤销后的黑名单聚合
     */
    public ClaimBlacklist revoke() {
        return new ClaimBlacklist(blacklistId, tenantId, subjectType, subjectId, subjectName,
                reasonCode, BlacklistStatus.REVOKED, effectiveTime);
    }

    /**
     * 命中判定：仅生效中条目参与反欺诈提示
     *
     * @return true 表示当前为生效黑名单
     */
    public boolean isHit() {
        return status == BlacklistStatus.ACTIVE;
    }
}
