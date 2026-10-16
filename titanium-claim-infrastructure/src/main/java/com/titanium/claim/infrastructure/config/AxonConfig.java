package com.titanium.claim.infrastructure.config;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.modelling.command.Repository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.claim.aggregate.Claim;

/**
 * Axon Framework 配置类
 *
 * <p>单体应用模式下，命令总线/事件存储均由 axon-spring-boot-starter 自动装配，
 * 此处仅显式声明理赔聚合根的事件溯源仓库。</p>
 */
@Configuration
public class AxonConfig {

    /**
     * 快照触发阈值：聚合累计事件数达到该值即生成快照。
     * <p>
     * m26-04 真机实测（2026-10-16）：本域单聚合累计事件数最多 9，远低于阈值。取 50 与全域统一口径一致（全仓实测最大为
     * regulatory 的 RegulatoryReport 54，其余均 ≤37）——存量聚合零快照开销，同时为事件流持续增长兜底：越过阈值后聚合加载只需「最新快照
     * + 增量事件」。
     * </p>
     */
    private static final int SNAPSHOT_TRIGGER_THRESHOLD = 50;

    /**
     * 理赔域快照触发器（经 {@link #claimRepository} 显式挂载）。
     */
    @Bean
    public SnapshotTriggerDefinition claimSnapshotTriggerDefinition(Snapshotter snapshotter) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, SNAPSHOT_TRIGGER_THRESHOLD);
    }

    /**
     * 配置理赔聚合根的事件溯源仓库（挂载快照触发器）
     * <p>
     * 🔴 触发器必须挂在 builder 上。本域以显式 {@code Repository<Claim>} Bean 装配仓库，Axon 的 Spring 集成直接复用该
     * Bean，{@code @Aggregate} 注解上的 {@code snapshotTriggerDefinition} 属性在这条装配路径上不生效（m25-01 实证）。
     * 若将来移除本 Bean 改由注解装配，须同步将注解属性指向本 Bean 名，否则快照将静默失效（无报错、无日志、无快照）。
     * </p>
     */
    @Bean
    public Repository<Claim> claimRepository(EventStore eventStore,
            SnapshotTriggerDefinition claimSnapshotTriggerDefinition) {
        return EventSourcingRepository.builder(Claim.class)
                .eventStore(eventStore)
                .snapshotTriggerDefinition(claimSnapshotTriggerDefinition)
                .build();
    }
}
