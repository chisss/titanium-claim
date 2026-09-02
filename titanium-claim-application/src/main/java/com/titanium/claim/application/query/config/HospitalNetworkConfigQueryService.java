package com.titanium.claim.application.query.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.repository.ClaimHospitalNetworkRepository;
import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 宠物医院网络配置查询服务（application 层配置子域读入口）
 * <p>
 * 配置子域为状态存储（无读模型投影），读请求直接查写侧仓储（§3.4.9 ⑥ 领域内部查询走写模型聚合），
 * 返回领域聚合由 web 层 MapStruct 组装 VO。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HospitalNetworkConfigQueryService {

    private final ClaimHospitalNetworkRepository repository;
    private final TenantContext                 tenantContext;

    /**
     * 按医院ID查询
     */
    @Transactional(readOnly = true)
    public ClaimHospitalNetwork getHospital(String hospitalId) {
        return repository.findById(tenantContext.getCurrentTenantId(), hospitalId)
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_CONFIG_NOT_FOUND,
                        "医院台账不存在: " + hospitalId));
    }

    /**
     * 查询租户下全部台账
     */
    @Transactional(readOnly = true)
    public List<ClaimHospitalNetwork> listHospitals() {
        return repository.findByTenant(tenantContext.getCurrentTenantId());
    }
}
