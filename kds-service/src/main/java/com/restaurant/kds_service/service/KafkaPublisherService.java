package com.restaurant.kds_service.service;

import com.restaurant.kds_service.dto.OrderReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for publishing order-ready events to Kafka
 */
@Service
public class KafkaPublisherService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPublisherService.class);

    private final KafkaTemplate<String, OrderReadyEvent> kafkaTemplate;
    private final String orderReadyTopic;

    public KafkaPublisherService(
            KafkaTemplate<String, OrderReadyEvent> kafkaTemplate,
            @Value("${spring.kafka.topic.order-ready}") String orderReadyTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderReadyTopic = orderReadyTopic;
    }

    /**
     * Publish order-ready event to Kafka
     * This is called ONLY after order status is successfully updated to READY
     */
    public void publishOrderReadyEvent(OrderReadyEvent event) {
        logger.info("Publishing order-ready event to Kafka - orderId: {}, tableId: {}",
                event.getOrderId(), event.getTableId());

        try {
            CompletableFuture<SendResult<String, OrderReadyEvent>> future =
                    kafkaTemplate.send(orderReadyTopic, event.getOrderId().toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.info("Order-ready event published successfully - orderId: {}, offset: {}",
                            event.getOrderId(), result.getRecordMetadata().offset());
                } else {
                    logger.error("Failed to publish order-ready event - orderId: {}, error: {}",
                            event.getOrderId(), ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            logger.error("Exception while publishing order-ready event - orderId: {}",
                    event.getOrderId(), e);
        }
    }
}

