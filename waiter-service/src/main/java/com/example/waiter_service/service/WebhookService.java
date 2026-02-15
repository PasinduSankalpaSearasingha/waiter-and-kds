package com.example.waiter_service.service;

import com.example.waiter_service.dto.OrderReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final RestTemplate restTemplate;
    private final String webhookUrl;

    public WebhookService(RestTemplate restTemplate, @Value("${webhook.url}") String webhookUrl) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
    }

    public void sendOrderReadyNotification(OrderReadyEvent event) {
        try {
            logger.info("Sending order ready notification to webhook: {}", webhookUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<OrderReadyEvent> request = new HttpEntity<>(event, headers);
            
            restTemplate.postForObject(webhookUrl, request, String.class);
            
            logger.info("Webhook notification sent successfully for order: {}", event.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to send webhook notification for order: {}", event.getOrderId(), e);
        }
    }
}
