# KDS Service - Kitchen Display System

## 📋 Overview

The **Kitchen Display Service (KDS)** is an orchestration service that:
- Polls the Order Service every 3 seconds for active orders
- Displays orders to kitchen staff via API endpoints
- Allows kitchen staff to mark orders as READY
- Publishes Kafka events when orders become READY

**Important:** KDS does NOT own a database. It acts as a workflow coordinator between Order Service, Kitchen Frontend, and downstream services via Kafka.

---

## 🏗️ Architecture

```
┌─────────────────┐
│  Kitchen UI     │
└────────┬────────┘
         │ HTTP
         ▼
┌─────────────────┐      Poll every 3s       ┌─────────────────┐
│   API Gateway   │◄────────────────────────►│  Order Service  │
│   (Port 8080)   │                          │   (Port 8086)   │
└────────┬────────┘                          └─────────────────┘
         │                                      SOURCE OF TRUTH
         │ /api/kitchen/*
         ▼
┌─────────────────┐
│   KDS Service   │
│   (Port 8085)   │
└────────┬────────┘
         │
         │ Kafka: order-ready
         ▼
┌─────────────────┐
│  Waiter Service │
└─────────────────┘
```

---

## 🔑 Key Design Principles

### 1️⃣ Order Service is the SOURCE OF TRUTH
- KDS never stores orders permanently
- All order data comes from polling Order Service
- Status updates go through Order Service first

### 2️⃣ Redis is OPTIONAL and CACHE ONLY
- Redis is used to speed up read operations
- If Redis fails, KDS continues with in-memory cache
- Redis is overwritten on every poll (not a database)

### 3️⃣ API Gateway is MANDATORY
- All calls to Order Service go through Gateway
- Base URL: `http://localhost:8080/api/orders`
- Authorization headers are forwarded automatically

### 4️⃣ Kafka Events are TRANSACTIONAL
- Events published ONLY after successful status update
- If Order Service update fails → NO Kafka event
- If Kafka fails → Log only (no rollback)

---

## 📦 Package Structure

```
com.restaurant.kds_service/
├── KdsServiceApplication.java         # Main app with @EnableScheduling
├── controller/
│   └── KitchenController.java         # Kitchen-facing REST API
├── service/
│   ├── KitchenService.java            # Main orchestration logic
│   ├── OrderPollingService.java       # Polls Order Service every 3s
│   └── KafkaPublisherService.java     # Publishes to Kafka
├── dto/
│   ├── KitchenOrderResponse.java      # Order display format
│   ├── OrderReadyEvent.java           # Kafka event payload
│   └── UpdateOrderStatusRequest.java  # Order Service request
└── config/
    ├── RestClientConfig.java          # RestTemplate bean
    ├── KafkaProducerConfig.java       # Kafka producer setup
    └── RedisConfig.java               # Optional Redis cache
```

---

## 🌐 API Endpoints

### 1. Get Active Orders
```http
GET /api/kitchen/orders
```

**Response:**
```json
[
  {
    "id": 101,
    "tableId": 5,
    "userId": 42,
    "status": "PREPARING",
    "totalAmount": 45.50,
    "createdAt": "2025-01-15T10:30:00",
    "items": [
      {
        "id": 1,
        "itemId": 10,
        "itemName": "Chicken Pizza",
        "quantity": 2,
        "unitPrice": 15.99
      }
    ]
  }
]
```

**Data Source Priority:**
1. Redis (if enabled and available)
2. In-memory cache (from last poll)
3. Empty list (if polling never succeeded)

---

### 2. Mark Order as READY
```http
POST /api/kitchen/orders/{orderId}/ready
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/kitchen/orders/101/ready
```

**Flow:**
1. ✅ Call Order Service: `PATCH /api/orders/101/status` → `{"status": "READY"}`
2. ✅ If successful → Publish Kafka event to `order-ready` topic
3. ❌ If Order Service fails → Return error, NO Kafka event

**Response:**
```json
{
  "id": 101,
  "tableId": 5,
  "userId": 42,
  "status": "READY",
  "totalAmount": 45.50,
  "createdAt": "2025-01-15T10:30:00",
  "items": [...]
}
```

---

## 📊 Polling Mechanism

### How It Works
```java
@Scheduled(fixedDelay = 3000)  // Every 3 seconds
public void pollActiveOrders() {
    // 1. Call Order Service via Gateway
    GET http://localhost:8080/api/orders/active
    
    // 2. Update in-memory cache (always)
    inMemoryOrders = response.getBody();
    
    // 3. Update Redis cache (if enabled)
    redisTemplate.set("kds:active-orders", orders, 10 seconds TTL);
}
```

### Active Orders Definition
From Order Service: Orders with status `CREATED`, `CONFIRMED`, or `PREPARING`

**Excluded:** `READY` and `SERVED` orders

### Error Handling
- If Order Service is down → KDS serves last known data
- Polling retries automatically every 3 seconds
- Logs errors but does NOT crash

---

## 🔥 Kafka Integration

### Topic: `order-ready`
**Producer:** KDS Service  
**Consumer:** Waiter Service

### Event Schema
```json
{
  "orderId": 101,
  "tableId": 5,
  "items": [
    {
      "itemName": "Chicken Pizza",
      "quantity": 2
    }
  ],
  "readyAt": "2025-01-15T10:45:00"
}
```

### When is the Event Published?
- ✅ ONLY after Order Service successfully updates status to `READY`
- ❌ NOT published if Order Service call fails
- ⚠️ If Kafka publish fails → Logged but order status remains `READY`

---

## 🗄️ Redis Cache (Optional)

### Configuration
```yaml
redis:
  enabled: true      # Set to false to disable
  host: localhost
  port: 6379
```

### Behavior
- **Key:** `kds:active-orders`
- **Value:** List of `KitchenOrderResponse` (JSON)
- **TTL:** 10 seconds
- **Updated:** Every 3 seconds by polling service

### Fallback Strategy
```
Redis enabled?
  ├─ Yes → Try Redis
  │   ├─ Success → Return cached data
  │   └─ Failure → Fall back to in-memory
  └─ No → Use in-memory directly
```

**Redis is NOT required** for KDS to function.

---

## ⚙️ Configuration

### application.yaml
```yaml
server:
  port: 8085

spring:
  application:
    name: kds-service
  kafka:
    bootstrap-servers: localhost:9092

# Order Service via API Gateway
order-service:
  base-url: http://localhost:8080/api/orders

# Kafka Topics
kafka:
  topic:
    order-ready: order-ready

# Redis (Optional)
redis:
  enabled: false
  host: localhost
  port: 6379
```

---

## 🚀 Running the Service

### Prerequisites
1. **Order Service** running on port 8086
2. **API Gateway** running on port 8080
3. **Kafka** running on port 9092
4. **Redis** (optional) running on port 6379

### Start KDS Service
```bash
cd services/kds-service
./mvnw spring-boot:run
```

Or with Maven Wrapper (Windows):
```powershell
.\mvnw.cmd spring-boot:run
```

### Verify It's Running
```bash
# Check health
curl http://localhost:8085/api/kitchen/orders

# Gateway access
curl http://localhost:8080/api/kitchen/orders
```

---

## 🧪 Testing the Flow

### 1. Create Orders in Order Service
```bash
# Ensure you have active orders with status CREATED/PREPARING
```

### 2. Check Kitchen Display
```bash
curl http://localhost:8080/api/kitchen/orders
```

### 3. Mark Order as Ready
```bash
curl -X POST http://localhost:8080/api/kitchen/orders/101/ready
```

### 4. Verify Kafka Event
Check Kafka topic `order-ready` for the published event.

---

## 📝 Implementation Summary

### What Was Implemented
✅ Polling every 3 seconds with `@Scheduled`  
✅ RestTemplate configured to call Order Service via Gateway  
✅ In-memory cache as primary fallback  
✅ Optional Redis cache layer  
✅ Kafka producer for `order-ready` events  
✅ Transactional flow: Order Service update → Kafka publish  
✅ Complete error handling and logging  

### What Was NOT Implemented
❌ Database (KDS is stateless)  
❌ Direct service-to-service URLs (Gateway only)  
❌ Order creation endpoints  
❌ WebSockets or Server-Sent Events  

---

## 🔍 Key Classes Explained

### OrderPollingService
- Polls Order Service every 3 seconds
- Maintains in-memory cache
- Optionally updates Redis
- Handles Order Service downtime gracefully

### KitchenService
- Main business logic
- Orchestrates status updates
- Ensures Kafka events only after successful updates

### KafkaPublisherService
- Publishes to `order-ready` topic
- Logs success/failure
- Non-blocking (async)

### KitchenController
- Exposes REST API
- Delegates to KitchenService
- Returns appropriate HTTP status codes

---

## 🛡️ Error Handling

| Scenario | Behavior |
|----------|----------|
| Order Service down during poll | Serve last known data, retry in 3s |
| Order Service down during status update | Return 500 error, NO Kafka event |
| Kafka publish fails | Log error, order status remains READY |
| Redis unavailable | Fall back to in-memory cache |
| Invalid orderId in ready request | Propagate error from Order Service |

---

## 📊 Logging

**Levels:**
- `INFO` - Polling results, status updates, Kafka events
- `WARN` - Redis failures (non-critical)
- `ERROR` - Order Service failures, Kafka failures
- `DEBUG` - Cache hits/misses, detailed flow

**Example Logs:**
```
✅ Polled 5 active orders from Order Service
✅ Order 101 status updated successfully in Order Service
✅ Order-ready event published successfully - orderId: 101, offset: 42
❌ Failed to poll Order Service: Connection refused
⚠️ Failed to update Redis cache (non-critical): Connection timeout
```

---

## 🔗 Integration Points

### Upstream Dependencies
- **Order Service** (via Gateway): Provides active orders, accepts status updates
- **API Gateway**: Routes all external calls

### Downstream Dependencies
- **Kafka**: Receives order-ready events
- **Waiter Service**: Consumes order-ready events

### Optional Dependencies
- **Redis**: Cache layer for performance

---

## 📚 References

- Order Service API: `GET /api/orders/active`, `PATCH /api/orders/{id}/status`
- API Gateway Config: `/gateway/src/main/resources/application.yaml`
- Kafka Topic: `order-ready`
- Redis Key: `kds:active-orders`

---

**Built with:** Spring Boot 4.0.2, Java 17, Kafka, Redis (optional)

