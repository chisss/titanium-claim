package com.titanium.claim.infrastructure.entity;

import com.titanium.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 宠物医院网络持久化对象（理赔配置支撑子域，状态存储）
 * <p>
 * 映射表 {@code t_claim_hospital_network}。聚合根 {@code ClaimHospitalNetwork} 与 DO 的转换经
 * {@code ClaimConfigPersistenceMapper}（MapStruct），协议状态枚举落库 code。
 * </p>
 */
@Entity
@Table(name = "t_claim_hospital_network",
        indexes = {
                @Index(name = "idx_claim_hospital_network_tenant", columnList = "tenant_id"),
                @Index(name = "idx_claim_hospital_network_name", columnList = "tenant_id,hospital_name")
        })
@Getter
@Setter
@NoArgsConstructor
public class ClaimHospitalNetworkDO extends BaseEntity {

    /** 医院ID（雪花，主键） */
    @Id
    @Column(name = "hospital_id", nullable = false, length = 32)
    private String hospitalId;

    /** 医院名称 */
    @Column(name = "hospital_name", nullable = false, length = 128)
    private String hospitalName;

    /** 医院等级（如 一级/二级/三级/宠物专科） */
    @Column(name = "hospital_level", length = 32)
    private String hospitalLevel;

    /** 协议状态 code（HospitalAgreementStatus） */
    @Column(name = "agreement_status", nullable = false, length = 32)
    private String agreementStatus;

    /** 定点赔付比例（0-100 百分比） */
    @Column(name = "payout_ratio")
    private Integer payoutRatio;

    /** 是否直赔医院（1=是 0=否） */
    @Column(name = "direct_settlement")
    private Boolean directSettlement;

    /** 医院地址 */
    @Column(name = "address", length = 256)
    private String address;

    /** 联系电话 */
    @Column(name = "contact_phone", length = 32)
    private String contactPhone;
}
