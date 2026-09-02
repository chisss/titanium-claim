package com.titanium.claim.repository;

import java.util.List;
import java.util.Optional;

import com.titanium.claim.aggregate.ClaimHospitalNetwork;

/**
 * 宠物医院网络聚合仓储接口（driven port，状态存储）
 * <p>
 * 物理实现位于 infrastructure 层（JPA 持久化 {@code t_claim_hospital_network}）。
 * 出险医院资格校验（宠物险线）按医院名称精确匹配台账。
 * </p>
 */
public interface ClaimHospitalNetworkRepository {

    /** 持久化（新增/全量更新，按 hospitalId upsert） */
    void save(ClaimHospitalNetwork hospital);

    /** 按租户 + 医院ID 查询 */
    Optional<ClaimHospitalNetwork> findById(String tenantId, String hospitalId);

    /** 查询租户下全部台账 */
    List<ClaimHospitalNetwork> findByTenant(String tenantId);

    /** 按医院名称精确查询租户台账（出险医院资格校验入口） */
    Optional<ClaimHospitalNetwork> findByName(String tenantId, String hospitalName);

    /** 逻辑删除 */
    void delete(String tenantId, String hospitalId);
}
