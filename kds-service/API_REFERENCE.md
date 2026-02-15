# KDS Service - Quick API Reference

## 🌐 Base URLs

**Direct Access:**
```
http://localhost:8085
```

**Via API Gateway (RECOMMENDED):**
```
http://localhost:8080
```

---

## 📋 Endpoints

### 1️⃣ Get Active Orders

**Description:** Fetch all orders with status CREATED, CONFIRMED, or PREPARING

**Endpoint:**
```http
GET /api/kitchen/orders
```

**cURL Example:**
```bash
# Via Gateway (recommended)
curl http://localhost:8080/api/kitchen/orders

# Direct access
curl http://localhost:8085/api/kitchen/orders
```

**Response (200 OK):**
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
      },
      {
        "id": 2,
        "itemId": 15,
        "itemName": "Caesar Salad",
        "quantity": 1,
        "unitPrice": 13.52
      }
    ]
  },
  {
    "id": 102,
    "tableId": 3,
    "userId": 25,
    "status": "CREATED",
    "totalAmount": 28.99,
    "createdAt": "2025-01-15T10:32:15",
    "items": [
      {
        "id": 3,
        "itemId": 12,
        "itemName": "Burger",
        "quantity": 1,
        "unitPrice": 12.99
      },
      {
        "id": 4,
        "itemId": 18,
        "itemName": "Fries",
        "quantity": 2,
        "unitPrice": 8.00
      }
    ]
  }
]
```

**Empty Response (No Active Orders):**
```json
[]
```

---

### 2️⃣ Mark Order as READY

**Description:** Update order status to READY and publish Kafka event

**Endpoint:**
```http
POST /api/kitchen/orders/{orderId}/ready
```

**Path Parameters:**
- `orderId` (Long) - The ID of the order to mark as ready

**cURL Examples:**

```bash
# Via Gateway (recommended)
curl -X POST http://localhost:8080/api/kitchen/orders/101/ready

# Direct access
curl -X POST http://localhost:8085/api/kitchen/orders/101/ready
```

**Success Response (200 OK):**
```json
{
  "id": 101,
  "tableId": 5,
  "userId": 42,
  "status": "READY",
  "totalAmount": 45.50,
  "createdAt": "2025-01-15T10:30:00",
  "items": [
    {
      "id": 1,
      "itemId": 10,
      "itemName": "Chicken Pizza",
      "quantity": 2,
      "unitPrice": 15.99
    },
    {
      "id": 2,
      "itemId": 15,
      "itemName": "Caesar Salad",
      "quantity": 1,
      "unitPrice": 13.52
    }
  ]
}
```

**Error Response (500 Internal Server Error):**
```json
null
```
*Check logs for detailed error message*

---

## 🎯 Common Use Cases

### Use Case 1: Kitchen Dashboard - Display Active Orders
```bash
# Frontend polls this every 5-10 seconds
curl http://localhost:8080/api/kitchen/orders
```

### Use Case 2: Kitchen Staff Marks Order Complete
```bash
# When kitchen finishes preparing order 105
curl -X POST http://localhost:8080/api/kitchen/orders/105/ready
```

### Use Case 3: Verify Order Status Changed
```bash
# After marking ready, verify it's no longer in active list
curl http://localhost:8080/api/kitchen/orders
```

---

## 📊 Data Flow

```
Kitchen UI
    │
    │ GET /api/kitchen/orders (every 5s)
    │
    ▼
API Gateway (8080)
    │
    ▼
KDS Service (8085)
    │
    ├─► Redis Cache (if enabled) ──┐
    │                               ├─► Returns cached orders
    └─► In-Memory Cache ────────────┘
         │
         │ Updated by background polling every 3s
         │
         ▼
    Order Service (8086)
```

```
Kitchen UI
    │
    │ POST /api/kitchen/orders/101/ready
    │
    ▼
API Gateway (8080)
    │
    ▼
KDS Service (8085)
    │
    │ Step 1: Update status
    ▼
Order Service (8086)
    │
    │ Returns updated order
    ▼
KDS Service (8085)
    │
    │ Step 2: Publish event (only if Step 1 succeeded)
    ▼
Kafka Topic: order-ready
    │
    ▼
Waiter Service
```

---

## 🔍 Testing Workflow

### Step 1: Ensure Order Service Has Active Orders
```bash
# Check Order Service directly
curl http://localhost:8086/api/orders/active

# Should return orders with status: CREATED, CONFIRMED, or PREPARING
```

### Step 2: Verify KDS Polling is Working
```bash
# Wait 3-6 seconds for polling to complete, then:
curl http://localhost:8080/api/kitchen/orders

# Should return same orders as Order Service
```

### Step 3: Mark an Order as Ready
```bash
# Replace 101 with actual order ID from Step 2
curl -X POST http://localhost:8080/api/kitchen/orders/101/ready
```

### Step 4: Verify Order is No Longer Active
```bash
# Order 101 should NOT appear in this list
curl http://localhost:8080/api/kitchen/orders
```

### Step 5: Check Kafka Topic
```bash
# Use Kafka console consumer or UI to verify event was published
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic order-ready --from-beginning
```

---

## 🧪 Postman Collection

### Get Active Orders

**Request:**
- Method: `GET`
- URL: `http://localhost:8080/api/kitchen/orders`
- Headers: None required
- Body: None

**Expected Response:**
- Status: 200 OK
- Body: Array of order objects

---

### Mark Order Ready

**Request:**
- Method: `POST`
- URL: `http://localhost:8080/api/kitchen/orders/{{orderId}}/ready`
- Headers: None required
- Body: None
- Path Variable: `orderId` = 101 (example)

**Expected Response:**
- Status: 200 OK
- Body: Updated order object with status "READY"

---

## ⚠️ Common Issues

### Issue: Empty array returned from GET /api/kitchen/orders

**Possible Causes:**
1. Order Service has no active orders
2. Polling hasn't completed yet (wait 3 seconds)
3. Order Service is down (check logs)

**Solution:**
```bash
# Check Order Service directly
curl http://localhost:8086/api/orders/active

# If it returns orders, KDS will sync in next 3s
```

---

### Issue: POST ready returns 500 error

**Possible Causes:**
1. Order doesn't exist
2. Order Service is down
3. Invalid order ID

**Solution:**
```bash
# Check KDS logs
tail -f logs/kds-service.log

# Verify order exists in Order Service
curl http://localhost:8086/api/orders/{orderId}
```

---

### Issue: Order status updated but Kafka event not published

**Expected Behavior:** This is intentional if Kafka is unavailable.

**Check:**
1. Order status should still be READY in Order Service
2. KDS logs should show Kafka error
3. Waiter service won't be notified (manual intervention needed)

---

## 📈 Performance Considerations

### Caching Strategy
- **Polling Interval:** 3 seconds
- **Redis TTL:** 10 seconds
- **Frontend Poll Interval:** Recommended 5-10 seconds

### Load Characteristics
- **Read-Heavy:** GET requests served from cache
- **Write-Light:** POST requests go through Order Service
- **Background Jobs:** One polling thread every 3s

### Scalability
- **Horizontal Scaling:** ✅ Yes (stateless service)
- **Redis Required:** ❌ No (works with in-memory)
- **Database Required:** ❌ No (reads from Order Service)

---

## 🔗 Related Services

| Service | Port | Purpose |
|---------|------|---------|
| Order Service | 8086 | Source of truth for orders |
| API Gateway | 8080 | Routing and auth |
| KDS Service | 8085 | Kitchen workflow |
| Kafka | 9092 | Event streaming |
| Redis | 6379 | Optional cache |

---

## 📞 Support

For issues or questions:
1. Check logs: `services/kds-service/logs/`
2. Verify dependencies are running
3. Test Order Service endpoints directly
4. Review KDS_SERVICE_README.md for architecture details

