package com.restaurant.kds_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing an order displayed in the kitchen
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrderResponse implements Serializable {

    private Long id;
    private Long tableId;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private List<OrderItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem implements Serializable {
        private Long id;
        private Long itemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}

