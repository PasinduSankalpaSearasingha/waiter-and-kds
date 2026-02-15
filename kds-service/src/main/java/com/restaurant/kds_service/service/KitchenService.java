package com.restaurant.kds_service.service;

import com.restaurant.kds_service.dto.KitchenOrderResponse;
import com.restaurant.kds_service.dto.OrderReadyEvent;
import com.restaurant.kds_service.dto.UpdateOrderStatusRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Service for kitchen operations
 * Orchestrates Order Service updates and Kafka event publishing
 */
@Service
public class KitchenService {

    private static final Logger logger = LoggerFactory.getLogger(KitchenService.class);

    private final RestTemplate restTemplate;
    private final KafkaPublisherService kafkaPublisherService;
    private final String orderServiceBaseUrl;

    public KitchenService(
            RestTemplate restTemplate,
            KafkaPublisherService kafkaPublisherService,
            @Value("${order-service.base-url}") String orderServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.kafkaPublisherService = kafkaPublisherService;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
    }

    /**
     * Mark order as READY
     * 1. Update Order Service status
     * 2. If successful → Publish Kafka event
     * 3. If failed → Throw exception (no Kafka event)
     */
    public KitchenOrderResponse markOrderAsReady(Long orderId) {
        logger.info("Marking order {} as READY", orderId);

        // Step 1: Update Order Service
        String url = orderServiceBaseUrl + "/" + orderId + "/status";
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest("READY");
        HttpEntity<UpdateOrderStatusRequest> requestEntity = new HttpEntity<>(request);

        try {
            logger.info("Calling Order Service to update order {} status to READY", orderId);
            ResponseEntity<KitchenOrderResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    requestEntity,
                    KitchenOrderResponse.class
            );

            KitchenOrderResponse updatedOrder = response.getBody();
            if (updatedOrder == null) {
                throw new RuntimeException("Order Service returned null response for orderId: " + orderId);
            }

            logger.info("Order {} status updated successfully in Order Service", orderId);

            // Step 2: Publish Kafka event (only if Order Service update succeeded)
            publishOrderReadyEvent(updatedOrder);

            return updatedOrder;

        } catch (Exception e) {
            logger.error("Failed to update order {} in Order Service: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to mark order as READY: " + e.getMessage(), e);
        }
    }

    /**
     * Update order status to any status (CREATED, PREPARING, READY, etc.)
     * Generic method for status updates without Kafka events
     */
    public KitchenOrderResponse updateOrderStatus(Long orderId, String status) {
        logger.info("Updating order {} status to {}", orderId, status);

        String url = orderServiceBaseUrl + "/" + orderId + "/status";
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(status);
        HttpEntity<UpdateOrderStatusRequest> requestEntity = new HttpEntity<>(request);

        try {
            logger.info("Calling Order Service to update order {} status to {}", orderId, status);
            ResponseEntity<KitchenOrderResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    requestEntity,
                    KitchenOrderResponse.class
            );

            KitchenOrderResponse updatedOrder = response.getBody();
            if (updatedOrder == null) {
                throw new RuntimeException("Order Service returned null response for orderId: " + orderId);
            }

            logger.info("Order {} status updated successfully to {} in Order Service", orderId, status);
            return updatedOrder;

        } catch (Exception e) {
            logger.error("Failed to update order {} to {}: {}", orderId, status, e.getMessage());
            throw new RuntimeException("Failed to update order status to " + status + ": " + e.getMessage(), e);
        }
    }

    /**
     * Publish order-ready event to Kafka
     */
    private void publishOrderReadyEvent(KitchenOrderResponse order) {
        try {
            OrderReadyEvent event = new OrderReadyEvent(
                    order.getId(),
                    order.getTableId(),
                    order.getItems().stream()
                            .map(item -> new OrderReadyEvent.OrderItem(
                                    item.getItemName(),
                                    item.getQuantity()
                            ))
                            .collect(Collectors.toList()),
                    LocalDateTime.now()
            );

            kafkaPublisherService.publishOrderReadyEvent(event);
            logger.info("Kafka event published for order {}", order.getId());

        } catch (Exception e) {
            // Log but don't fail the request - order is already READY in Order Service
            logger.error("Failed to publish Kafka event for order {} (order is still READY): {}",
                    order.getId(), e.getMessage());
        }
    }
}

