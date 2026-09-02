package com.titanium.claim.application.command.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.application.model.config.ClaimHospitalNetworkConfigRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.enums.config.HospitalAgreementStatus;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;
import com.titanium.common.util.SnowflakeIdGenerator;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 宠物医院网络配置写服务（application 层配置子域写入口，薄）
 * <p>
 * 医院台账 CRUD + 协议状态流转（暂停/恢复/终止，聚合行为内聚）。配置子域为状态存储支撑数据
 * （根规约 §4.1 纯 JPA CRUD），不经过 Axon 命令网关、不发事件。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalNetworkConfigService {

    private final ClaimHospitalNetworkRepository repository;
    private final TenantContext                 tenantContext;

    /**
     * 新增/更新医院台账（hospitalId 空=新增，非空=全量更新）
     *
     * @return 医院ID
     */
    @Transactional
    public String saveHospitalNetwork(ClaimHospitalNetworkConfigRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        if (request.getHospitalId() == null || request.getHospitalId().isBlank()) {
            ClaimHospitalNetwork hospital = ClaimHospitalNetwork.create(SnowflakeIdGenerator.generate(),
                    tenantId, request.getHospitalName(), request.getHospitalLevel(),
                    HospitalAgreementStatus.fromCode(request.getAgreementStatus()), request.getPayoutRatio(),
                    request.getDirectSettlement(), request.getAddress(), request.getContactPhone());
            repository.save(hospital);
            log.info("新增医院台账, tenantId={}, hospitalId={}", tenantId, hospital.getHospitalId());
            return hospital.getHospitalId();
        }
        ClaimHospitalNetwork existing = repository.findById(tenantId, request.getHospitalId())
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "医院台账不存在: " + request.getHospitalId()));
        ClaimHospitalNetwork updated = existing.update(request.getHospitalName(), request.getHospitalLevel(),
                HospitalAgreementStatus.fromCode(request.getAgreementStatus()), request.getPayoutRatio(),
                request.getDirectSettlement(), request.getAddress(), request.getContactPhone());
        repository.save(updated);
        return updated.getHospitalId();
    }

    /**
     * 协议暂停：资格冻结，恢复前不按定点比例赔付
     */
    @Transactional
    public void suspendHospital(String hospitalId) {
        repository.findById(tenantContext.getCurrentTenantId(), hospitalId)
                .ifPresentOrElse(hospital -> repository.save(hospital.suspend()),
                        () -> { throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                                "医院台账不存在: " + hospitalId); });
    }

    /**
     * 协议恢复：重新具备定点资格
     */
    @Transactional
    public void activateHospital(String hospitalId) {
        repository.findById(tenantContext.getCurrentTenantId(), hospitalId)
                .ifPresentOrElse(hospital -> repository.save(hospital.activate()),
                        () -> { throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                                "医院台账不存在: " + hospitalId); });
    }

    /**
     * 协议终止：台账留存，不再参与资格校验
     */
    @Transactional
    public void terminateHospital(String hospitalId) {
        repository.findById(tenantContext.getCurrentTenantId(), hospitalId)
                .ifPresentOrElse(hospital -> repository.save(hospital.terminate()),
                        () -> { throw new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                                "医院台账不存在: " + hospitalId); });
    }

    /**
     * 逻辑删除医院台账
     */
    @Transactional
    public void deleteHospitalNetwork(String hospitalId) {
        repository.delete(tenantContext.getCurrentTenantId(), hospitalId);
    }
}
