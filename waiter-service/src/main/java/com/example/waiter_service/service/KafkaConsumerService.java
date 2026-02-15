package com.example.waiter_service.service;

import com.example.waiter_service.dto.OrderReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

@Service
public class KafkaConsumerService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private WebhookService webhookService;

    private final List<OrderReadyEvent> receivedOrders = Collections.synchronizedList(new ArrayList<>());

    @KafkaListener(topics = "${spring.kafka.topic.order-ready}", groupId = "waiter-group-v2")
    public void listen(OrderReadyEvent event) {
        System.out.println("Received Order: " + event);
        receivedOrders.add(0, event); // Add to beginning
        if (receivedOrders.size() > 50) {
            receivedOrders.remove(receivedOrders.size() - 1);
        }
        messagingTemplate.convertAndSend("/topic/orders", event);
        webhookService.sendOrderReadyNotification(event);
    }

    public List<OrderReadyEvent> getReceivedOrders() {
        return new ArrayList<>(receivedOrders);
    }
}