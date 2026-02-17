package com.example.waiter_service.service;

import com.example.waiter_service.dto.OrderReadyEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
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
    private final List<String> rawMessages = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private int messageCount = 0;

    @KafkaListener(topics = "${spring.kafka.topic.order-ready}", groupId = "waiter-group-v2")
    public void listen(ConsumerRecord<String, OrderReadyEvent> record) {
        messageCount++;
        System.out.println(">>> KAFKA MESSAGE RECEIVED! Count: " + messageCount);
        System.out.println(">>> Topic: " + record.topic() + ", Partition: " + record.partition() + ", Offset: " + record.offset());
        System.out.println(">>> Key: " + record.key());
        System.out.println(">>> Value: " + record.value());
        
        try {
            OrderReadyEvent event = record.value();
            if (event != null) {
                System.out.println(">>> Deserialized event - orderId: " + event.getOrderId() + ", tableId: " + event.getTableId());
                receivedOrders.add(0, event);
                if (receivedOrders.size() > 50) {
                    receivedOrders.remove(receivedOrders.size() - 1);
                }
                messagingTemplate.convertAndSend("/topic/orders", event);
                webhookService.sendOrderReadyNotification(event);
            } else {
                String errMsg = "Event was null after deserialization at offset " + record.offset();
                System.out.println(">>> ERROR: " + errMsg);
                errors.add(errMsg);
            }
        } catch (Exception e) {
            String errMsg = "Error processing message at offset " + record.offset() + ": " + e.getMessage();
            System.out.println(">>> ERROR: " + errMsg);
            errors.add(errMsg);
            e.printStackTrace();
        }
    }

    // Raw string listener on a DIFFERENT consumer group to independently verify messages
    @KafkaListener(topics = "${spring.kafka.topic.order-ready}", groupId = "waiter-debug-raw-v1",
                   properties = {
                       "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                       "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
                   })
    public void listenRaw(ConsumerRecord<String, String> record) {
        String raw = "RAW[offset=" + record.offset() + "]: " + record.value();
        System.out.println(">>> " + raw);
        rawMessages.add(0, raw);
        if (rawMessages.size() > 20) {
            rawMessages.remove(rawMessages.size() - 1);
        }
    }

    public List<OrderReadyEvent> getReceivedOrders() {
        return new ArrayList<>(receivedOrders);
    }

    public List<String> getRawMessages() {
        return new ArrayList<>(rawMessages);
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public int getMessageCount() {
        return messageCount;
    }
}
