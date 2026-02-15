package com.restaurant.kds_service.service;

import com.restaurant.kds_service.dto.OrderReadyEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka Producer functionality in KDS Service
 * Tests the complete flow of publishing order-ready events to Kafka
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"order-ready-test"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "security.protocol=PLAINTEXT"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "kafka.topic.order-ready=order-ready-test",
        "redis.enabled=false"
})
class KafkaProducerIntegrationTest {

    @Autowired
    private KafkaPublisherService kafkaPublisherService;

    @Value("${kafka.topic.order-ready}")
    private String orderReadyTopic;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaMessageListenerContainer<String, OrderReadyEvent> container;
    private BlockingQueue<ConsumerRecord<String, OrderReadyEvent>> records;

    @BeforeEach
    void setUp() {
        // Set up a Kafka consumer to verify messages
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderReadyEvent.class.getName());

        DefaultKafkaConsumerFactory<String, OrderReadyEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProperties = new ContainerProperties(orderReadyTopic);
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        records = new LinkedBlockingQueue<>();

        container.setupMessageListener((MessageListener<String, OrderReadyEvent>) record -> {
            System.out.println("Test Consumer received: " + record.value());
            records.add(record);
        });

        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void testPublishOrderReadyEvent_Success() throws InterruptedException {
        // Arrange
        Long orderId = 101L;
        Long tableId = 5L;
        OrderReadyEvent.OrderItem item1 = new OrderReadyEvent.OrderItem("Chicken Pizza", 2);
        OrderReadyEvent.OrderItem item2 = new OrderReadyEvent.OrderItem("Caesar Salad", 1);

        OrderReadyEvent event = new OrderReadyEvent(
                orderId,
                tableId,
                Arrays.asList(item1, item2),
                LocalDateTime.now()
        );

        // Act
        kafkaPublisherService.publishOrderReadyEvent(event);

        // Assert - Wait for message to be consumed (max 10 seconds)
        ConsumerRecord<String, OrderReadyEvent> receivedRecord = records.poll(10, TimeUnit.SECONDS);

        assertNotNull(receivedRecord, "Should receive a message from Kafka");
        assertEquals(orderId.toString(), receivedRecord.key(), "Message key should be orderId");

        OrderReadyEvent receivedEvent = receivedRecord.value();
        assertNotNull(receivedEvent, "Event should not be null");
        assertEquals(orderId, receivedEvent.getOrderId(), "Order ID should match");
        assertEquals(tableId, receivedEvent.getTableId(), "Table ID should match");
        assertEquals(2, receivedEvent.getItems().size(), "Should have 2 items");

        // Verify items
        OrderReadyEvent.OrderItem receivedItem1 = receivedEvent.getItems().get(0);
        assertEquals("Chicken Pizza", receivedItem1.getItemName());
        assertEquals(2, receivedItem1.getQuantity());

        OrderReadyEvent.OrderItem receivedItem2 = receivedEvent.getItems().get(1);
        assertEquals("Caesar Salad", receivedItem2.getItemName());
        assertEquals(1, receivedItem2.getQuantity());

        assertNotNull(receivedEvent.getReadyAt(), "Ready timestamp should be set");
    }

    @Test
    void testPublishMultipleEvents_AllReceived() throws InterruptedException {
        // Arrange
        OrderReadyEvent event1 = new OrderReadyEvent(
                201L, 10L,
                Arrays.asList(new OrderReadyEvent.OrderItem("Burger", 1)),
                LocalDateTime.now()
        );

        OrderReadyEvent event2 = new OrderReadyEvent(
                202L, 11L,
                Arrays.asList(new OrderReadyEvent.OrderItem("Pasta", 2)),
                LocalDateTime.now()
        );

        OrderReadyEvent event3 = new OrderReadyEvent(
                203L, 12L,
                Arrays.asList(new OrderReadyEvent.OrderItem("Steak", 1)),
                LocalDateTime.now()
        );

        // Act
        kafkaPublisherService.publishOrderReadyEvent(event1);
        kafkaPublisherService.publishOrderReadyEvent(event2);
        kafkaPublisherService.publishOrderReadyEvent(event3);

        // Assert - Receive all 3 messages
        ConsumerRecord<String, OrderReadyEvent> received1 = records.poll(10, TimeUnit.SECONDS);
        ConsumerRecord<String, OrderReadyEvent> received2 = records.poll(10, TimeUnit.SECONDS);
        ConsumerRecord<String, OrderReadyEvent> received3 = records.poll(10, TimeUnit.SECONDS);

        assertNotNull(received1, "Should receive first message");
        assertNotNull(received2, "Should receive second message");
        assertNotNull(received3, "Should receive third message");

        assertEquals(201L, received1.value().getOrderId());
        assertEquals(202L, received2.value().getOrderId());
        assertEquals(203L, received3.value().getOrderId());
    }

    @Test
    void testEventSerialization_AllFieldsPreserved() throws InterruptedException {
        // Arrange
        LocalDateTime timestamp = LocalDateTime.of(2026, 2, 12, 14, 30, 0);
        OrderReadyEvent event = new OrderReadyEvent(
                999L,
                88L,
                Arrays.asList(
                        new OrderReadyEvent.OrderItem("Item A", 5),
                        new OrderReadyEvent.OrderItem("Item B", 3),
                        new OrderReadyEvent.OrderItem("Item C", 1)
                ),
                timestamp
        );

        // Act
        kafkaPublisherService.publishOrderReadyEvent(event);

        // Assert
        ConsumerRecord<String, OrderReadyEvent> received = records.poll(10, TimeUnit.SECONDS);
        assertNotNull(received);

        OrderReadyEvent receivedEvent = received.value();
        assertEquals(999L, receivedEvent.getOrderId());
        assertEquals(88L, receivedEvent.getTableId());
        assertEquals(3, receivedEvent.getItems().size());

        // Verify all items preserved
        assertEquals("Item A", receivedEvent.getItems().get(0).getItemName());
        assertEquals(5, receivedEvent.getItems().get(0).getQuantity());
        assertEquals("Item B", receivedEvent.getItems().get(1).getItemName());
        assertEquals(3, receivedEvent.getItems().get(1).getQuantity());
        assertEquals("Item C", receivedEvent.getItems().get(2).getItemName());
        assertEquals(1, receivedEvent.getItems().get(2).getQuantity());
    }

    @Test
    void testPublishWithEmptyItems_Success() throws InterruptedException {
        // Arrange - Order with no items (edge case)
        OrderReadyEvent event = new OrderReadyEvent(
                500L,
                25L,
                Arrays.asList(),
                LocalDateTime.now()
        );

        // Act
        kafkaPublisherService.publishOrderReadyEvent(event);

        // Assert
        ConsumerRecord<String, OrderReadyEvent> received = records.poll(10, TimeUnit.SECONDS);
        assertNotNull(received, "Should still publish event even with empty items");
        assertEquals(500L, received.value().getOrderId());
        assertEquals(0, received.value().getItems().size());
    }

    @Test
    void testMessageKey_MatchesOrderId() throws InterruptedException {
        // Arrange
        Long orderId = 777L;
        OrderReadyEvent event = new OrderReadyEvent(
                orderId,
                1L,
                Arrays.asList(new OrderReadyEvent.OrderItem("Test Item", 1)),
                LocalDateTime.now()
        );

        // Act
        kafkaPublisherService.publishOrderReadyEvent(event);

        // Assert
        ConsumerRecord<String, OrderReadyEvent> received = records.poll(10, TimeUnit.SECONDS);
        assertNotNull(received);

        // Key should be the orderId as String for proper partitioning
        assertEquals(orderId.toString(), received.key(),
                "Message key should be orderId to ensure ordering per order");
    }
}

