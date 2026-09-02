package com.titanium.claim.web.response.config;

import lombok.Data;

/**
 * 宠物医院网络配置 VO（web 前端出参，理赔配置中心）
 * <p>
 * 由 {@code ClaimConfigWebMapper} 自 {@code ClaimHospitalNetwork} 聚合组装（MapStruct），
 * 协议状态枚举落 code。
 * </p>
 */
@Data
public class ClaimHospitalNetworkConfigVO {
    /** 医院ID */
    private String  hospitalId;
    /** 医院名称 */
    private String  hospitalName;
    /** 医院等级 */
    private String  hospitalLevel;
    /** 协议状态 code（ACTIVE/SUSPENDED/TERMINATED） */
    private String  agreementStatus;
    /** 定点赔付比例（0-100 百分比） */
    private Integer payoutRatio;
    /** 是否直赔医院 */
    private Boolean directSettlement;
    /** 医院地址 */
    private String  address;
    /** 联系电话 */
    private String  contactPhone;
}
