package com.restaurant.kds_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Event published to Kafka when an order is marked as READY
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderReadyEvent implements Serializable {

    private Long orderId;
    private Long tableId;
    private List<OrderItem> items;
    private LocalDateTime readyAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem implements Serializable {
        private String itemName;
        private Integer quantity;
    }
}
