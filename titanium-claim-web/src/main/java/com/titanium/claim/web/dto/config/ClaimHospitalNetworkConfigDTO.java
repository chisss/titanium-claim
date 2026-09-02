package com.titanium.claim.web.dto.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 宠物医院网络配置 DTO（web 前端入参，理赔配置中心）
 * <p>
 * 新增/更新合一：{@code hospitalId} 空=新增，非空=全量更新。经 {@code ClaimConfigWebMapper}
 * 翻译为应用层配置写模型。
 * </p>
 */
@Data
public class ClaimHospitalNetworkConfigDTO {
    /** 医院ID（空=新增） */
    private String  hospitalId;
    /** 医院名称 */
    @NotBlank(message = "医院名称不能为空")
    private String  hospitalName;
    /** 医院等级（如 一级/二级/三级/宠物专科） */
    private String  hospitalLevel;
    /** 协议状态 code（ACTIVE/SUSPENDED/TERMINATED） */
    @NotBlank(message = "协议状态不能为空")
    private String  agreementStatus;
    /** 定点赔付比例（0-100 百分比） */
    @Min(value = 0, message = "定点赔付比例不能小于 0")
    @Max(value = 100, message = "定点赔付比例不能大于 100")
    private Integer payoutRatio;
    /** 是否直赔医院 */
    private Boolean directSettlement;
    /** 医院地址 */
    private String  address;
    /** 联系电话 */
    private String  contactPhone;
}
