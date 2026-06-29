package com.titanium.claim.infrastructure.event;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimUpdatedEvent;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class KafkaEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @EventHandler
    public void handle(ClaimCreatedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_CREATED,
                           event.claimId().toString(), eventJson);
    }

    @EventHandler
    public void handle(ClaimUpdatedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_UPDATED,
                           event.claimId().toString(), eventJson);
    }

    @EventHandler
    public void handle(ClaimStatusChangedEvent event) {
        String eventJson = JSON.toJSONString(event);
        kafkaTemplate.send(ClaimConstants.KafkaTopic.CLAIM_STATUS_CHANGED,
                           event.claimId().toString(), eventJson);
    }
}
