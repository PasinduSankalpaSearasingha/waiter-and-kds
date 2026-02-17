package com.restaurant.kds_service.controller;

import com.restaurant.kds_service.dto.KitchenOrderResponse;
import com.restaurant.kds_service.service.KitchenService;
import com.restaurant.kds_service.service.OrderPollingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Kitchen Display System
 * Provides endpoints for kitchen staff to view and manage orders
 */
@RestController
@RequestMapping("/api/kitchen")
@CrossOrigin(origins = "*")
public class KitchenController {

    private static final Logger logger = LoggerFactory.getLogger(KitchenController.class);

    private final OrderPollingService orderPollingService;
    private final KitchenService kitchenService;

    public KitchenController(OrderPollingService orderPollingService, KitchenService kitchenService) {
        this.orderPollingService = orderPollingService;
        this.kitchenService = kitchenService;
    }

    /**
     * Get all active orders for kitchen display
     * Data source: Redis cache (if enabled) then In-memory cache then Empty list
     */
    @GetMapping("/orders")
    public ResponseEntity<List<KitchenOrderResponse>> getActiveOrders() {
        logger.info("GET /api/kitchen/orders - Fetching active orders");
        List<KitchenOrderResponse> orders = orderPollingService.getActiveOrders();
        logger.info("Returning {} active orders", orders.size());
        return ResponseEntity.ok(orders);
    }

    /**
     * Mark an order as READY
     * Flow:
     * 1. Update Order Service status to READY
     * 2. If successful then Publish Kafka event
     * 3. If failed then Return error (no Kafka event)
     */
    @PostMapping("/orders/{orderId}/ready")
    public ResponseEntity<KitchenOrderResponse> markOrderReady(
            @PathVariable Long orderId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Table-Id", required = false) String tableId) {
        logger.info("POST /api/kitchen/orders/{}/ready - Marking order as READY (userId: {}, tableId: {})",
                orderId, userId, tableId);
        KitchenOrderResponse updatedOrder = kitchenService.markOrderAsReady(orderId, authHeader, userId, tableId);
        logger.info("Order {} marked as READY successfully", orderId);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Change order status to PREPARING
     */
    @PostMapping("/orders/{orderId}/preparing")
    public ResponseEntity<KitchenOrderResponse> markOrderPreparing(
            @PathVariable Long orderId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Table-Id", required = false) String tableId) {
        logger.info("POST /api/kitchen/orders/{}/preparing - Marking order as PREPARING (userId: {}, tableId: {})",
                orderId, userId, tableId);
        KitchenOrderResponse updatedOrder = kitchenService.updateOrderStatus(orderId, "PREPARING", authHeader, userId, tableId);
        logger.info("Order {} marked as PREPARING successfully", orderId);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Change order status to CREATED
     */
    @PostMapping("/orders/{orderId}/created")
    public ResponseEntity<KitchenOrderResponse> markOrderCreated(
            @PathVariable Long orderId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Table-Id", required = false) String tableId) {
        logger.info("POST /api/kitchen/orders/{}/created - Marking order as CREATED (userId: {}, tableId: {})",
                orderId, userId, tableId);
        KitchenOrderResponse updatedOrder = kitchenService.updateOrderStatus(orderId, "CREATED", authHeader, userId, tableId);
        logger.info("Order {} marked as CREATED successfully", orderId);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("KDS Service is running");
    }

    /**
     * Debug endpoint: test order service connectivity and show config
     */
    @GetMapping("/debug/status")
    public ResponseEntity<java.util.Map<String, Object>> debugStatus() {
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        
        // Show config
        String jaas = System.getenv("JAAS_CONFIG");
        if (jaas == null) {
            status.put("JAAS_CONFIG", "NOT SET (null)");
        } else if (jaas.isEmpty()) {
            status.put("JAAS_CONFIG", "EMPTY STRING");
        } else {
            status.put("JAAS_CONFIG", jaas.substring(0, Math.min(60, jaas.length())) + "... (length=" + jaas.length() + ")");
        }
        status.put("BOOTSTRAP_SERVERS", System.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS"));
        status.put("ORDER_SERVICE_BASE_URL", System.getenv("ORDER_SERVICE_BASE_URL"));
        
        // Show cached orders count
        status.put("cachedOrdersCount", orderPollingService.getActiveOrders().size());
        
        // Test order service connectivity
        try {
            org.springframework.http.ResponseEntity<String> response = new org.springframework.web.client.RestTemplate()
                .getForEntity("https://gateway-app.mangofield-91faac5e.southeastasia.azurecontainerapps.io/api/orders/active", String.class);
            status.put("orderServiceStatus", response.getStatusCode().toString());
            status.put("orderServiceResponseLength", response.getBody() != null ? response.getBody().length() : 0);
        } catch (Exception e) {
            status.put("orderServiceStatus", "ERROR: " + e.getMessage());
        }
        
        return ResponseEntity.ok(status);
    }
    /**
     * TEST ENDPOINT: Bypass Order Service and publish to Kafka directly
     * Use this to verify Azure Event Hubs connection independently
     */
    @PostMapping("/test/orders/{orderId}/ready")
    public ResponseEntity<String> testMarkOrderReady(
            @PathVariable Long orderId,
            @RequestParam(required = false, defaultValue = "1") Long tableId) {
        
        logger.info("TEST POST /api/kitchen/test/orders/{}/ready - Publishing dummy event", orderId);
        
        try {
            // Create dummy items
            java.util.List<com.restaurant.kds_service.dto.OrderReadyEvent.OrderItem> items = java.util.List.of(
                new com.restaurant.kds_service.dto.OrderReadyEvent.OrderItem("Test Item 1", 2),
                new com.restaurant.kds_service.dto.OrderReadyEvent.OrderItem("Test Item 2", 1)
            );

            com.restaurant.kds_service.dto.OrderReadyEvent event = new com.restaurant.kds_service.dto.OrderReadyEvent(
                    orderId,
                    tableId,
                    items,
                    java.time.LocalDateTime.now()
            );

            // Access the private service field via reflection or just make it public?
            // Better: Expose a method in KitchenService or just inject KafkaPublisherService here?
            // Actually, we can use the kitchenService to publish if we add a method there, OR
            // we can just add KafkaPublisherService to this controller.
            // Let's add KafkaPublisherService dependency to this controller to keep it simple for now.
             
             // Wait, modifying the constructor signature is a larger change.
             // Let's add a public method to KitchenService that just publishes.
             kitchenService.publishTestEvent(event);
             
            return ResponseEntity.ok("Test Kafka event published for Order " + orderId);
        } catch (Exception e) {
            logger.error("Failed to publish test event", e);
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }
}

