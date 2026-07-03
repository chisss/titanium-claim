package com.titanium.claim.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 理赔服务启动类（组合根）
 * <p>
 * 写侧为 Axon 事件溯源聚合根（无 JPA 写模型实体）；读侧读模型 {@code query.view} 由投影维护，
 * 故 JPA 仅扫描读侧 {@code query.view} / {@code query.repository}。 {@code @EnableScheduling}
 * 驱动读侧 DLQ 重试。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.titanium.claim")
@EntityScan(basePackages = "com.titanium.claim.query.view")
@EnableJpaRepositories(basePackages = "com.titanium.claim.query.repository")
@EnableFeignClients(basePackages = { "com.titanium.claim.api.client", "com.titanium.policy.api" })
public class ClaimApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimApplication.class, args);
    }
}
