package com.titanium.claim.query.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.query.view.ClaimView;

/**
 * 理赔案件读模型仓储
 * <p>
 * CQRS 查询侧仓储，访问读模型表 {@code t_claim_view}。租户隔离经 {@code tenantId} 条件下推。
 * </p>
 */
@Repository
public interface ClaimViewRepository
        extends JpaRepository<ClaimView, String>, JpaSpecificationExecutor<ClaimView> {

    Optional<ClaimView> findByClaimId(String claimId);

    Optional<ClaimView> findByClaimIdAndTenantId(String claimId, String tenantId);

    List<ClaimView> findByCustomerIdAndTenantId(String customerId, String tenantId);

    List<ClaimView> findByPolicyIdAndTenantId(String policyId, String tenantId);

    List<ClaimView> findByStatusAndTenantId(ClaimStatus status, String tenantId);

    /**
     * 按车辆识别代号（VIN）+ 出险日期区间检索历史赔案（G11/g11-03 NCD 反算取数出口）
     * <p>
     * 区间为出险日期 {@code [from, to)} 半开区间（与今日报案统计同口径）；排除指定状态的案件
     * （拒赔不构成「历史赔案次数」，由调用方传 {@code REJECTED}）。VIN 是跨保单的车辆身份，
     * 续保换单后 policyId/subjectId 均变，唯 VIN 不变——故 NCD 历史检索以 VIN 为主键。
     * </p>
     *
     * @param tenantId 租户ID（多租户隔离）
     * @param vin 车辆识别代号
     * @param from 出险日期起（含）
     * @param to 出险日期止（不含）
     * @param excludedStatus 排除的理赔状态（NCD 场景传 {@code REJECTED}）
     * @return 区间内命中的赔案读模型列表
     */
    List<ClaimView> findByTenantIdAndVinAndIncidentDateGreaterThanEqualAndIncidentDateLessThanAndStatusNot(
            String tenantId, String vin, LocalDateTime from, LocalDateTime to, ClaimStatus excludedStatus);

    /**
     * 按标的ID + 出险日期区间检索历史赔案（VIN 缺失时的保单内标的兜底路径，语义同上）
     *
     * @param tenantId 租户ID（多租户隔离）
     * @param subjectId 标的ID（保单内唯一）
     * @param from 出险日期起（含）
     * @param to 出险日期止（不含）
     * @param excludedStatus 排除的理赔状态（NCD 场景传 {@code REJECTED}）
     * @return 区间内命中的赔案读模型列表
     */
    List<ClaimView> findByTenantIdAndSubjectIdAndIncidentDateGreaterThanEqualAndIncidentDateLessThanAndStatusNot(
            String tenantId, String subjectId, LocalDateTime from, LocalDateTime to, ClaimStatus excludedStatus);

    /**
     * 按租户ID查询全部理赔案件列表：默认按创建时间倒序 + 主键第二排序键
     * <p>
     * 排序下推到数据库：调用方在其结果上做 skip/limit 分页时，窗口必须落在有确定顺序的结果集上。
     * </p>
     */
    List<ClaimView> findByTenantIdOrderByCreateTimeDescClaimIdDesc(String tenantId);

    /**
     * 按租户ID统计理赔案件总数（多租户隔离）
     *
     * @param tenantId 租户ID
     * @return 理赔案件总数
     */
    long countByTenantId(String tenantId);

    /**
     * 按状态集合 + 租户ID统计理赔案件数（待处理数用未结案状态集合）
     *
     * @param statuses 状态集合
     * @param tenantId 租户ID
     * @return 命中状态的案件数
     */
    long countByStatusInAndTenantId(Collection<ClaimStatus> statuses, String tenantId);

    /**
     * 按创建时间区间 + 租户ID统计报案数（今日报案用当天 00:00~次日 00:00 半开区间）
     *
     * @param tenantId 租户ID
     * @param start 起始时间（含）
     * @param end 结束时间（不含）
     * @return 区间内报案数
     */
    long countByTenantIdAndCreateTimeGreaterThanEqualAndCreateTimeLessThan(String tenantId, LocalDateTime start,
            LocalDateTime end);

    /**
     * 累计已结案赔付金额（指定状态集合案件的核定赔付金额之和，多租户隔离）
     * <p>
     * 用 {@code COALESCE} 兜底空结果返回 0，避免无数据时返回 null。强制携带 {@code tenantId}。
     * </p>
     * <p>
     * 🔴 须传<b>终态集合</b>（{@code PAID} + {@code CLOSED}）：{@code PAID} 只是「已支付待归档」的
     * 中转态，归档后案件转 {@code CLOSED}。若只过滤 {@code PAID}，赔付金额会在归档瞬间从统计中消失，
     * 「累计」指标随业务推进反向下降。参考 D-501-48。
     * </p>
     *
     * @param statuses 结案状态集合（{@code PAID}、{@code CLOSED}）
     * @param tenantId 租户ID
     * @return 已结案赔付金额之和，无数据为 0
     */
    @Query("SELECT COALESCE(SUM(c.settledAmount), 0) FROM ClaimView c "
            + "WHERE c.tenantId = :tenantId AND c.status IN :statuses")
    BigDecimal sumSettledAmountByStatusInAndTenantId(@Param("statuses") List<ClaimStatus> statuses,
            @Param("tenantId") String tenantId);
}
