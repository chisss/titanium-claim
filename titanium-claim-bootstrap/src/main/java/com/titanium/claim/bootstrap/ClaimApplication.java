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
 * 写侧理赔案件为 Axon 事件溯源聚合根（无 JPA 写模型实体）；理赔配置中心（流程模板/赔付规则/
 * 医院网络/黑名单/单证模板/快赔规则/时限规则）为状态存储，DO 实体与 Spring Data 仓储位于
 * {@code infrastructure.entity} / {@code infrastructure.repository.jpa}；读侧读模型
 * {@code query.view} 由投影维护。故 JPA 实体扫描覆盖基础设施与读侧两个包，仓储扫描同理。
 * {@code @EnableScheduling} 驱动读侧 DLQ 重试。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.titanium.claim")
@EntityScan(basePackages = { "com.titanium.claim.query.view", "com.titanium.claim.infrastructure.entity" })
@EnableJpaRepositories(basePackages = { "com.titanium.claim.query.repository",
        "com.titanium.claim.infrastructure.repository.jpa" })
@EnableFeignClients(basePackages = { "com.titanium.claim.api.client", "com.titanium.policy.api",
        "com.titanium.clause.api" })
public class ClaimApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimApplication.class, args);
    }
}
