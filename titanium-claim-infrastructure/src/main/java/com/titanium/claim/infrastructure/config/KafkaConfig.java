package com.titanium.claim.infrastructure.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka配置类
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Kafka Admin配置
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * 创建Kafka主题
     */
    @Bean
    public NewTopic claimCreatedTopic() {
        return new NewTopic("claim-created", 3, (short) 1);
    }

    @Bean
    public NewTopic claimUpdatedTopic() {
        return new NewTopic("claim-updated", 3, (short) 1);
    }

    @Bean
    public NewTopic claimStatusChangedTopic() {
        return new NewTopic("claim-status-changed", 3, (short) 1);
    }

    @Bean
    public NewTopic claimApprovedTopic() {
        return new NewTopic("claim-approved", 3, (short) 1);
    }

    @Bean
    public NewTopic claimRejectedTopic() {
        return new NewTopic("claim-rejected", 3, (short) 1);
    }

    @Bean
    public NewTopic claimPaidTopic() {
        return new NewTopic("claim-paid", 3, (short) 1);
    }

    /**
     * Kafka生产者工厂配置
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka模板配置
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}