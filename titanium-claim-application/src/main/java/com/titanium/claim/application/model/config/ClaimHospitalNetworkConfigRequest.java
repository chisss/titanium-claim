package com.titanium.claim.application.model.config;

import lombok.Data;

/**
 * 宠物医院网络配置入参（application 层配置子域写模型）
 * <p>
 * 新增/更新合一：{@code hospitalId} 为空表示新增（application 雪花生成），非空表示全量更新。
 * 协议状态 code 经 {@code HospitalAgreementStatus.fromCode} 还原枚举。
 * </p>
 */
@Data
public class ClaimHospitalNetworkConfigRequest {
    /** 医院ID（空=新增） */
    private String  hospitalId;
    /** 医院名称 */
    private String  hospitalName;
    /** 医院等级（如 一级/二级/三级/宠物专科） */
    private String  hospitalLevel;
    /** 协议状态 code（HospitalAgreementStatus） */
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
