package com.titanium.claim.infrastructure.projection;

import java.util.List;
import java.util.stream.Collectors;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimUpdatedEvent;
import com.titanium.claim.infrastructure.mapper.ClaimMapper;
import com.titanium.claim.infrastructure.repository.entity.ClaimEntity;
import com.titanium.claim.infrastructure.repository.jpa.JpaClaimRepository;
import com.titanium.claim.query.ClaimQuery;
import com.titanium.claim.query.FindClaimsByCustomerIdQuery;
import com.titanium.claim.query.FindClaimsByPolicyIdQuery;
import com.titanium.claim.query.FindClaimsByStatusQuery;

import lombok.RequiredArgsConstructor;

/**
 * 理赔案件投影类
 * <p>
 * 用于处理理赔案件领域事件并更新数据库投影，以及处理查询请求
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ClaimProjection {

    private final JpaClaimRepository jpaClaimRepository;

    private final ClaimMapper claimMapper;

    /**
     * 处理理赔案件创建事件
     *
     * @param event 理赔案件创建事件
     */
    @EventHandler
    public void handle(ClaimCreatedEvent event) {
        // 创建理赔案件实体
        com.titanium.claim.aggregate.Claim claim = new com.titanium.claim.aggregate.Claim();
        claim.setClaimId(event.claimId());
        claim.setCustomerId(event.customerId());
        claim.setPolicyId(event.policyId());
        claim.setClaimNumber(event.claimNumber());
        claim.setClaimType(event.claimType());
        claim.setIncidentDate(event.incidentDate());
        claim.setIncidentDescription(event.incidentDescription());
        claim.setClaimAmount(event.claimAmount());

        // 保存到数据库
        ClaimEntity entity = claimMapper.toEntity(claim, "default"); // 实际应用中需要从TenantContext获取租户ID
        entity.setCreateTime(event.createdAt());
        entity.setUpdateTime(event.createdAt());
        jpaClaimRepository.save(entity);
    }

    /**
     * 处理理赔案件更新事件
     *
     * @param event 理赔案件更新事件
     */
    @EventHandler
    public void handle(ClaimUpdatedEvent event) {
        // 查找并更新理赔案件实体
        jpaClaimRepository.findById(event.claimId().value())
                .ifPresent(entity -> {
                    entity.setClaimType(event.claimType());
                    entity.setIncidentDate(event.incidentDate());
                    entity.setIncidentDescription(event.incidentDescription());
                    entity.setClaimAmount(event.claimAmount().value());
                    entity.setUpdateTime(event.updatedAt());
                    jpaClaimRepository.save(entity);
                });
    }

    /**
     * 处理理赔案件状态变更事件
     *
     * @param event 理赔案件状态变更事件
     */
    @EventHandler
    public void handle(ClaimStatusChangedEvent event) {
        // 查找并更新理赔案件状态
        jpaClaimRepository.findById(event.claimId().value())
                .ifPresent(entity -> {
                    entity.setStatus(event.newStatus());
                    entity.setUpdateTime(event.changedAt());
                    jpaClaimRepository.save(entity);
                });
    }

    /**
     * 处理理赔案件查询
     *
     * @param query 理赔案件查询
     * @return 理赔案件
     */
    @QueryHandler
    public com.titanium.claim.aggregate.Claim handle(ClaimQuery query) {
        return jpaClaimRepository.findById(query.claimId().value())
                .map(claimMapper::toDomain)
                .orElse(null);
    }

    /**
     * 处理根据客户ID查询理赔案件列表
     *
     * @param query 根据客户ID查询理赔案件列表
     * @return 理赔案件列表
     */
    @QueryHandler
    public List<com.titanium.claim.aggregate.Claim> handle(FindClaimsByCustomerIdQuery query) {
        return jpaClaimRepository.findByCustomerId(query.customerId().value())
                .stream()
                .map(claimMapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * 处理根据保单ID查询理赔案件列表
     *
     * @param query 根据保单ID查询理赔案件列表
     * @return 理赔案件列表
     */
    @QueryHandler
    public List<com.titanium.claim.aggregate.Claim> handle(FindClaimsByPolicyIdQuery query) {
        return jpaClaimRepository.findByPolicyId(query.policyId().value())
                .stream()
                .map(claimMapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * 处理根据状态查询理赔案件列表
     *
     * @param query 根据状态查询理赔案件列表
     * @return 理赔案件列表
     */
    @QueryHandler
    public List<com.titanium.claim.aggregate.Claim> handle(FindClaimsByStatusQuery query) {
        return jpaClaimRepository.findByStatus(query.status())
                .stream()
                .map(claimMapper::toDomain)
                .collect(Collectors.toList());
    }
}
