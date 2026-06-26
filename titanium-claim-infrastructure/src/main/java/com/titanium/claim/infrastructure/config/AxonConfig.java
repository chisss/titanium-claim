package com.titanium.claim.infrastructure.config;

import org.axonframework.eventsourcing.EventSourcingRepository;
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
     * 配置理赔聚合根的事件溯源仓库
     */
    @Bean
    public Repository<Claim> claimRepository(EventStore eventStore) {
        return EventSourcingRepository.builder(Claim.class)
                .eventStore(eventStore)
                .build();
    }
}
