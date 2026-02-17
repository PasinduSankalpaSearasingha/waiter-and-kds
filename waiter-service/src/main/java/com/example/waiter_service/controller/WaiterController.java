package com.example.waiter_service.controller;

import com.example.waiter_service.dto.OrderReadyEvent;
import com.example.waiter_service.service.KafkaConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/waiter")
@CrossOrigin(origins = "*")
public class WaiterController {

    @Autowired
    private KafkaConsumerService kafkaConsumerService;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @GetMapping("/debug/kafka")
    public ResponseEntity<Map<String, Object>> getKafkaStatus() {
        Map<String, Object> status = new HashMap<>();
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            status.put(container.getListenerId(), container.isRunning());
        }
        status.put("listenerCount", kafkaListenerEndpointRegistry.getListenerContainers().size());
        status.put("messageCount", kafkaConsumerService.getMessageCount());
        status.put("receivedOrderCount", kafkaConsumerService.getReceivedOrders().size());
        status.put("errors", kafkaConsumerService.getErrors());
        status.put("rawMessages", kafkaConsumerService.getRawMessages());
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public String health() {
        return "Waiter Service is running on port 8086!";
    }

    // Matches the user's requested URL: /api/waiter/received-orders
    @GetMapping("/received-orders")
    public ResponseEntity<List<OrderReadyEvent>> getReceivedOrders() {
        return ResponseEntity.ok(kafkaConsumerService.getReceivedOrders());
    }

    @GetMapping("/orders/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveOrders() {
        Map<String, Object> order1 = new HashMap<>();
        order1.put("id", 1L);
        order1.put("tableId", 101L);
        order1.put("status", "CREATED");
        order1.put("createdAt", LocalDateTime.now());
        order1.put("items", Arrays.asList(
            createItem(1L, "BBQ Chicken Wings", 2),
            createItem(2L, "French Fries", 1)
        ));

        Map<String, Object> order2 = new HashMap<>();
        order2.put("id", 2L);
        order2.put("tableId", 102L);
        order2.put("status", "PREPARING");
        order2.put("createdAt", LocalDateTime.now().minusMinutes(5));
        order2.put("items", Arrays.asList(
            createItem(3L, "Burgers", 1)
        ));

        return ResponseEntity.ok(Arrays.asList(order1, order2));
    }

    @RequestMapping(value = "/orders/{orderId}/status", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", orderId);
        response.put("tableId", 100L + orderId);
        response.put("status", body.getOrDefault("status", "READY"));
        response.put("createdAt", LocalDateTime.now());
        response.put("items", Arrays.asList(
                createItem(orderId, "Mock Item " + orderId, 1)
        ));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createItem(Long id, String name, int qty) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("itemName", name);
        item.put("quantity", qty);
        return item;
    }
}
