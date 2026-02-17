package com.example.waiter_service.controller;

import com.example.waiter_service.dto.OrderReadyEvent;
import com.example.waiter_service.service.KafkaConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
        
        // JAAS_CONFIG diagnostic (masked for security)
        String jaas = System.getenv("JAAS_CONFIG");
        if (jaas == null) {
            status.put("JAAS_CONFIG", "NOT SET (null)");
        } else if (jaas.isEmpty()) {
            status.put("JAAS_CONFIG", "EMPTY STRING");
        } else {
            // Show first 60 chars + length for verification without exposing the key
            String masked = jaas.substring(0, Math.min(60, jaas.length())) + "... (length=" + jaas.length() + ")";
            status.put("JAAS_CONFIG", masked);
        }
        
        status.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", System.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS"));
        
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

}
