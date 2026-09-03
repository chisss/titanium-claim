package com.titanium.claim.aggregate;

import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 宠物医院网络聚合根（理赔配置支撑子域，状态存储）
 * <p>
 * 宠物险定点/直赔医院台账：名称/等级/协议状态/赔付比例/地址/联系电话（产品文档 §4.2）。
 * 出险医院资格校验（宠物险线）仅认定协议 {@code ACTIVE} 态医院为定点，自动套用其赔付比例。
 * </p>
 */
@Getter
public final class ClaimHospitalNetwork {

    private final String                   hospitalId;
    private final String                   tenantId;
    /** 医院名称 */
    private final String                   hospitalName;
    /** 医院等级（如 一级/二级/三级/宠物专科） */
    private final String                   hospitalLevel;
    /** 协议状态（ACTIVE/SUSPENDED/TERMINATED，持久化 code） */
    private final HospitalAgreementStatus  agreementStatus;
    /** 定点赔付比例（0-100 百分比，直赔/定点报销套用） */
    private final Integer                  payoutRatio;
    /** 是否直赔医院（true 免客户垫付，结算直连） */
    private final Boolean                  directSettlement;
    /** 医院地址 */
    private final String                   address;
    /** 联系电话 */
    private final String                   contactPhone;

    private ClaimHospitalNetwork(String hospitalId, String tenantId, String hospitalName, String hospitalLevel,
                                 HospitalAgreementStatus agreementStatus, Integer payoutRatio,
                                 Boolean directSettlement, String address, String contactPhone) {
        if (hospitalName == null || hospitalName.isBlank()) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "医院名称不能为空");
        }
        if (payoutRatio != null && (payoutRatio < 0 || payoutRatio > 100)) {
            throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_INVALID, "定点赔付比例必须在 0-100 之间");
        }
        this.hospitalId = hospitalId;
        this.tenantId = tenantId;
        this.hospitalName = hospitalName;
        this.hospitalLevel = hospitalLevel;
        this.agreementStatus = agreementStatus;
        this.payoutRatio = payoutRatio;
        this.directSettlement = directSettlement;
        this.address = address;
        this.contactPhone = contactPhone;
    }

    /**
     * 创建医院网络台账条目
     *
     * @param hospitalId       医院ID（application 雪花生成）
     * @param tenantId         租户ID（平台默认 'platform'）
     * @param hospitalName     医院名称
     * @param hospitalLevel    医院等级
     * @param agreementStatus  协议状态
     * @param payoutRatio      定点赔付比例（0-100）
     * @param directSettlement 是否直赔医院
     * @param address          医院地址
     * @param contactPhone     联系电话
     * @return 医院网络聚合
     */
    public static ClaimHospitalNetwork create(String hospitalId, String tenantId, String hospitalName,
                                              String hospitalLevel, HospitalAgreementStatus agreementStatus,
                                              Integer payoutRatio, Boolean directSettlement,
                                              String address, String contactPhone) {
        return new ClaimHospitalNetwork(hospitalId, tenantId, hospitalName, hospitalLevel, agreementStatus,
                payoutRatio, directSettlement, address, contactPhone);
    }

    /**
     * 全量更新台账条目（后台表单全量提交，返回新实例）
     *
     * @return 更新后的医院网络聚合
     */
    public ClaimHospitalNetwork update(String hospitalName, String hospitalLevel,
                                       HospitalAgreementStatus agreementStatus, Integer payoutRatio,
                                       Boolean directSettlement, String address, String contactPhone) {
        return new ClaimHospitalNetwork(hospitalId, tenantId, hospitalName, hospitalLevel, agreementStatus,
                payoutRatio, directSettlement, address, contactPhone);
    }

    /**
     * 协议暂停：资格冻结，恢复前不按定点比例赔付。仅 ACTIVE 态可暂停。
     *
     * @return 暂停后的医院网络聚合
     */
    public ClaimHospitalNetwork suspend() {
        ensureStatusIn("暂停协议", HospitalAgreementStatus.ACTIVE);
        return new ClaimHospitalNetwork(hospitalId, tenantId, hospitalName, hospitalLevel,
                HospitalAgreementStatus.SUSPENDED, payoutRatio, directSettlement, address, contactPhone);
    }

    /**
     * 协议恢复：重新具备定点资格。仅 SUSPENDED 态可恢复。
     *
     * @return 恢复后的医院网络聚合
     */
    public ClaimHospitalNetwork activate() {
        ensureStatusIn("恢复协议", HospitalAgreementStatus.SUSPENDED);
        return new ClaimHospitalNetwork(hospitalId, tenantId, hospitalName, hospitalLevel,
                HospitalAgreementStatus.ACTIVE, payoutRatio, directSettlement, address, contactPhone);
    }

    /**
     * 协议终止：台账留存，不再参与资格校验。仅 ACTIVE/SUSPENDED 态可终止（终态不可逆）。
     *
     * @return 终止后的医院网络聚合
     */
    public ClaimHospitalNetwork terminate() {
        ensureStatusIn("终止协议", HospitalAgreementStatus.ACTIVE, HospitalAgreementStatus.SUSPENDED);
        return new ClaimHospitalNetwork(hospitalId, tenantId, hospitalName, hospitalLevel,
                HospitalAgreementStatus.TERMINATED, payoutRatio, directSettlement, address, contactPhone);
    }

    /**
     * 状态前置校验：协议状态机仅允许合法流转，非法流转抛业务异常。
     *
     * @param operation 操作名称
     * @param allowed   允许执行该操作的当前状态
     */
    private void ensureStatusIn(String operation, HospitalAgreementStatus... allowed) {
        for (HospitalAgreementStatus status : allowed) {
            if (agreementStatus == status) {
                return;
            }
        }
        throw new BusinessException(ClaimErrorCode.CLAIM_STATUS_TRANSITION_INVALID,
                String.format("医院[%s] 当前协议状态为 %s，禁止执行「%s」", hospitalName, agreementStatus, operation));
    }

    /**
     * 定点资格判定：仅协议有效态参与出险医院资格校验
     *
     * @return true 表示当前为有效定点/直赔医院
     */
    public boolean isEligible() {
        return agreementStatus == HospitalAgreementStatus.ACTIVE;
    }
}
