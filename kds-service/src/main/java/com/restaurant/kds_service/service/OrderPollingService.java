package com.restaurant.kds_service.service;

import com.restaurant.kds_service.dto.KitchenOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for polling Order Service at configurable interval
 * Order Service is the SOURCE OF TRUTH
 * Redis is optional cache ONLY
 */
@Service
public class OrderPollingService {

    private static final Logger logger = LoggerFactory.getLogger(OrderPollingService.class);
    private static final String REDIS_KEY = "kds:active-orders";

    private final RestTemplate restTemplate;
    private final String orderServiceBaseUrl;
    private final boolean redisEnabled;
    private final long pollingIntervalMs;

    @Autowired(required = false)
    private RedisTemplate<String, List<KitchenOrderResponse>> redisTemplate;

    // In-memory backup (always maintained regardless of Redis)
    private final List<KitchenOrderResponse> inMemoryOrders = new CopyOnWriteArrayList<>();

    public OrderPollingService(
            RestTemplate restTemplate,
            @Value("${order-service.base-url}") String orderServiceBaseUrl,
            @Value("${redis.enabled:false}") boolean redisEnabled,
            @Value("${polling.interval-ms:3000}") long pollingIntervalMs) {
        this.restTemplate = restTemplate;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
        this.redisEnabled = redisEnabled;
        this.pollingIntervalMs = pollingIntervalMs;
        logger.info("OrderPollingService initialized - polling interval: {}ms ({}s)",
                pollingIntervalMs, pollingIntervalMs / 1000.0);
    }

    /**
     * Poll Order Service at configurable interval (default: 3 seconds)
     * This is the ONLY way data enters the KDS system
     */
    @Scheduled(fixedDelayString = "${polling.interval-ms:3000}")
    public void pollActiveOrders() {
        logger.debug("Polling Order Service for active orders...");

        try {
            String url = orderServiceBaseUrl + "/active";

            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<List<KitchenOrderResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<List<KitchenOrderResponse>>() {}
            );

            List<KitchenOrderResponse> activeOrders = response.getBody();
            if (activeOrders == null) {
                activeOrders = new ArrayList<>();
            }

            logger.info("Polled {} active orders from Order Service", activeOrders.size());

            // Update in-memory cache (always)
            inMemoryOrders.clear();
            inMemoryOrders.addAll(activeOrders);

            // Update Redis cache if enabled
            if (redisEnabled && redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(REDIS_KEY, activeOrders, 10, TimeUnit.SECONDS);
                    logger.debug("Updated Redis cache with {} orders", activeOrders.size());
                } catch (Exception e) {
                    logger.warn("Failed to update Redis cache (non-critical): {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Failed to poll Order Service: {}", e.getMessage());
            logger.debug("Full error:", e);
            // KDS continues to operate with last known data
        }
    }

    /**
     * Get active orders from cache
     * Priority: Redis (if enabled) → In-memory → Empty list
     */
    public List<KitchenOrderResponse> getActiveOrders() {
        // Try Redis first if enabled
        if (redisEnabled && redisTemplate != null) {
            try {
                List<KitchenOrderResponse> cachedOrders = redisTemplate.opsForValue().get(REDIS_KEY);
                if (cachedOrders != null) {
                    logger.debug("Serving {} orders from Redis cache", cachedOrders.size());
                    return cachedOrders;
                }
            } catch (Exception e) {
                logger.warn("Failed to read from Redis (falling back to in-memory): {}", e.getMessage());
            }
        }

        // Fallback to in-memory
        logger.debug("Serving {} orders from in-memory cache", inMemoryOrders.size());
        return new ArrayList<>(inMemoryOrders);
    }
}

