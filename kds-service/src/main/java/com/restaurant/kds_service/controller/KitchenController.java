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
}

