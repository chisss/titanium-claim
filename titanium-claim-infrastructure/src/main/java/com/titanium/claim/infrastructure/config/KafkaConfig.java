package com.titanium.claim.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

/**
 * Kafka配置类
 * <p>
 * 本域依赖裸 {@code spring-kafka}（非 spring-boot-starter-kafka），无 Boot 自动配置，
 * 故须显式声明：① {@code @EnableKafka} 注册 {@code @KafkaListener} 注解后处理器；
 * ② 消费者工厂与监听器容器工厂，否则 {@code PaymentResultConsumer} 的端点无容器工厂可用，
 * 支付回写监听器静默不消费。
 * </p>
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

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

    @Bean
    public NewTopic paymentOrderCreatedTopic() {
        return new NewTopic("payment-order-created", 3, (short) 1);
    }

    @Bean
    public NewTopic paymentOrderPaidTopic() {
        return new NewTopic("payment-order-paid", 3, (short) 1);
    }

    /**
     * Kafka生产者工厂配置
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 发布方 KafkaEventPublisher 以 fastjson2 生成 JSON String 发送，序列化器须用 StringSerializer，
        // 避免 JsonSerializer 对已是 String 的 payload 二次 JSON 编码（外层再套引号），
        // 使下游 StringDeserializer + JSONObject.parseObject 解析出转义字符串、字段取值恒 null。
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka模板配置
     * <p>
     * 泛型为 {@code <String, String>}，与 {@code KafkaEventPublisher} 注入类型一致（发 fastjson2 JSON String）。
     * </p>
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 消费者工厂
     * <p>
     * 入站统一 String 反序列化：防腐监听器以 {@code JSONObject.parseObject(payload, XxxMessage.class)}
     * 一次反序列化，不依赖上游域类型。
     * </p>
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka监听器容器工厂（{@code @KafkaListener} 默认工厂名，不可改名）
     */
    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
