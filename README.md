# TipCurrent

A production-quality, open-source engagement backend for live streaming platforms. TipCurrent handles tips, reactions, and other engagement events that drive interaction and monetization in gaming, live events, webinars, and creator content.

## Overview

TipCurrent provides a reliable, self-hosted solution for ingesting and broadcasting engagement events from live streaming platforms. It supports multiple event types with real-time WebSocket broadcasting, webhook notifications, and pre-aggregated analytics.

## Features

- **Tips API** - Create and query tip events with sender, recipient, and amount
- **Reactions API** - Create and query emoji reactions to streams/content
- **Real-time WebSocket Broadcasting** - Instant event delivery to subscribers
- **Webhooks** - Event notifications to external systems with HMAC signature verification and retries
- **Idempotency** - Safe retries via `Idempotency-Key` header
- **Analytics API** - Pre-aggregated statistics with OLTP/OLAP separation
- **Scheduled Aggregation** - Hourly stats computation for tips and reactions
- PostgreSQL persistence with proper indexing
- Docker Compose for easy local development
- 61 integration tests with Testcontainers
- Built with Spring Boot 4.0.1 and Java 25

## Prerequisites

- Java 25 or higher
- Maven 3.9+
- Docker and Docker Compose
- curl or similar HTTP client (for testing)

## Quick Start

### 1. Start the Database

```bash
docker-compose up -d
```

This starts a PostgreSQL 17 container with the database pre-configured.

### 2. Build the Application

On macOS/Linux:
```bash
./mvnw clean package
```

On Windows:
```bash
mvnw.cmd clean package
```

### 3. Run the Application

On macOS/Linux:
```bash
./mvnw spring-boot:run
```

On Windows:
```bash
mvnw.cmd spring-boot:run
```

The service will start on `http://localhost:8080`.

### 4. Create Your First Tip

```bash
curl -X POST http://localhost:8080/api/tips \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": "gaming_stream_123",
    "senderId": "alice",
    "recipientId": "bob",
    "amount": 100.00,
    "message": "Great play!",
    "metadata": "{\"type\":\"celebration\"}"
  }'
```

### 5. Create Your First Reaction

```bash
curl -X POST http://localhost:8080/api/reactions \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": "gaming_stream_123",
    "userId": "alice",
    "emoji": "🔥",
    "targetId": "msg_123"
  }'
```

## API Reference

### Tips API

#### Create Tip

**Endpoint:** `POST /api/tips`

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| Idempotency-Key | No | UUID for safe retries (24-hour expiration) |

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| roomId | string | Yes | Identifier for the room/stream |
| senderId | string | Yes | User sending the tip |
| recipientId | string | Yes | User receiving the tip |
| amount | decimal | Yes | Tip amount (e.g., tokens, currency units) |
| message | string | No | Optional message (max 1000 chars) |
| metadata | string | No | Optional JSON metadata |

**Response:** HTTP 201 Created (or 200 OK if idempotency key matched existing tip)

```json
{
  "id": 1,
  "roomId": "gaming_stream_123",
  "senderId": "alice",
  "recipientId": "bob",
  "amount": 100.00,
  "message": "Great play!",
  "metadata": "{\"type\":\"celebration\"}",
  "createdAt": "2024-01-15T10:30:45.123Z"
}
```

#### List Tips

**Endpoint:** `GET /api/tips`

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| roomId | string | No | Filter by room ID |
| senderId | string | No | Filter by sender ID |
| recipientId | string | No | Filter by recipient ID |
| page | integer | No | Page number (default: 0) |
| size | integer | No | Page size (default: 20) |

**Examples:**

```bash
# Get all tips
curl http://localhost:8080/api/tips

# Get tips for a room
curl http://localhost:8080/api/tips?roomId=gaming_stream_123

# Get tips received by a user
curl http://localhost:8080/api/tips?recipientId=bob
```

#### Get Tip by ID

**Endpoint:** `GET /api/tips/{id}`

**Response:** HTTP 200 OK or 404 Not Found

---

### Reactions API

#### Create Reaction

**Endpoint:** `POST /api/reactions`

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| Idempotency-Key | No | UUID for safe retries (24-hour expiration) |

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| roomId | string | Yes | Identifier for the room/stream |
| userId | string | Yes | User sending the reaction |
| emoji | string | Yes | Emoji reaction (e.g., "🔥", "❤️", "👍") |
| targetId | string | No | ID of content being reacted to (message, moment, etc.) |
| metadata | string | No | Optional JSON metadata |

**Response:** HTTP 201 Created (or 200 OK if idempotency key matched)

```json
{
  "id": 1,
  "roomId": "gaming_stream_123",
  "userId": "alice",
  "emoji": "🔥",
  "targetId": "msg_123",
  "metadata": null,
  "createdAt": "2024-01-15T10:30:45.123Z"
}
```

#### List Reactions

**Endpoint:** `GET /api/reactions`

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| roomId | string | No | Filter by room ID |
| userId | string | No | Filter by user ID |
| emoji | string | No | Filter by emoji (requires roomId) |
| targetId | string | No | Filter by target ID |
| page | integer | No | Page number (default: 0) |
| size | integer | No | Page size (default: 20) |

**Examples:**

```bash
# Get all reactions
curl http://localhost:8080/api/reactions

# Get reactions for a room
curl http://localhost:8080/api/reactions?roomId=gaming_stream_123

# Get fire reactions in a room
curl "http://localhost:8080/api/reactions?roomId=gaming_stream_123&emoji=🔥"

# Get reactions to a specific message
curl http://localhost:8080/api/reactions?targetId=msg_123
```

#### Get Reaction by ID

**Endpoint:** `GET /api/reactions/{id}`

**Response:** HTTP 200 OK or 404 Not Found

---

## Real-Time Updates via WebSocket

TipCurrent broadcasts all events (tips and reactions) in real-time using WebSocket with STOMP protocol.

### WebSocket Endpoint

Connect to: `ws://localhost:8080/ws`

### Subscription Pattern

Subscribe to room-specific topics to receive all events:

**Topic:** `/topic/rooms/{roomId}`

Both tips and reactions for that room are broadcast to the same topic.

### JavaScript Client Example

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);

    stompClient.subscribe('/topic/rooms/gaming_stream_123', function(message) {
        const event = JSON.parse(message.body);

        // Check event type by presence of fields
        if (event.amount !== undefined) {
            console.log('Tip received:', event);
            displayTip(event);
        } else if (event.emoji !== undefined) {
            console.log('Reaction received:', event);
            displayReaction(event);
        }
    });
});
```

### Use Cases

- **Live Stream Overlays**: Display tips and reactions in real-time
- **Audience Engagement**: Show reaction bursts during exciting moments
- **Creator Dashboards**: Real-time revenue and engagement tracking
- **Moderation Tools**: Monitor activity across rooms

---

## Analytics API

TipCurrent provides production-quality analytics using **pre-aggregated summary tables** for both tips and reactions.

### Architecture

```
Event Created → events tables (OLTP, write-optimized)
                     ↓
              @Scheduled job (runs hourly at :05)
                     ↓
         room_stats_hourly (pre-aggregated summary table)
                     ↓
    Analytics API → Fast reads from summary table only
```

**Key Benefits:**
- No resource contention between writes and analytics
- Predictable, fast query performance
- Includes both tip and reaction statistics

**Trade-offs:**
- Data freshness: Up to 1 hour lag

### Get Room Statistics

**Endpoint:** `GET /api/analytics/rooms/{roomId}/stats`

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| startDate | instant | No | Filter from this time (ISO 8601) |
| endDate | instant | No | Filter to this time (ISO 8601) |

**Response:**

```json
{
  "roomId": "gaming_stream_123",
  "stats": [
    {
      "periodStart": "2024-01-15T10:00:00Z",
      "periodEnd": "2024-01-15T11:00:00Z",
      "totalTips": 25,
      "totalAmount": 2500.00,
      "uniqueSenders": 12,
      "uniqueRecipients": 3,
      "averageTipAmount": 100.00,
      "totalReactions": 150,
      "uniqueReactors": 45
    }
  ],
  "summary": {
    "totalTips": 25,
    "totalAmount": 2500.00,
    "averageTipAmount": 100.00,
    "totalReactions": 150
  }
}
```

---

## Webhooks

TipCurrent provides webhooks for real-time event notifications to external systems.

### Supported Events

| Event | Description |
|-------|-------------|
| `tip.created` | Triggered when a new tip is created |
| `reaction.created` | Triggered when a new reaction is created |

### Webhook Management API

#### Create Webhook

**Endpoint:** `POST /api/webhooks`

```json
{
  "url": "https://your-platform.com/webhooks/tipcurrent",
  "event": "tip.created",
  "secret": "your-webhook-secret",
  "roomId": "gaming_stream_123",
  "description": "Tip notifications for room 123"
}
```

Note: `roomId` is optional. If omitted, webhook receives events from all rooms.

#### Other Endpoints

- `GET /api/webhooks` - List all webhooks
- `GET /api/webhooks/{id}` - Get webhook details
- `DELETE /api/webhooks/{id}` - Delete webhook
- `PATCH /api/webhooks/{id}/enable` - Enable webhook
- `PATCH /api/webhooks/{id}/disable` - Disable webhook
- `POST /api/webhooks/{id}/test` - Send test payload
- `GET /api/webhooks/{id}/deliveries` - View delivery logs

### Webhook Payload

**Headers:**
```
Content-Type: application/json
X-TipCurrent-Signature: <HMAC-SHA256 signature>
X-TipCurrent-Event: tip.created
X-TipCurrent-Delivery-Attempt: 1
```

**Body (tip.created):**
```json
{
  "id": 1,
  "roomId": "gaming_stream_123",
  "senderId": "alice",
  "recipientId": "bob",
  "amount": 100.00,
  "message": "Great play!",
  "createdAt": "2024-01-15T10:30:45.123Z"
}
```

**Body (reaction.created):**
```json
{
  "id": 1,
  "roomId": "gaming_stream_123",
  "userId": "alice",
  "emoji": "🔥",
  "targetId": "msg_123",
  "createdAt": "2024-01-15T10:30:45.123Z"
}
```

### Signature Verification

TipCurrent signs payloads using HMAC-SHA256. Verify the `X-TipCurrent-Signature` header:

```python
import hmac, hashlib, base64

def verify_signature(payload, signature, secret):
    expected = base64.b64encode(
        hmac.new(secret.encode(), payload.encode(), hashlib.sha256).digest()
    ).decode()
    return hmac.compare_digest(expected, signature)
```

### Delivery Guarantees

- Automatic retry: 3 attempts with exponential backoff
- Connection timeout: 5 seconds
- Request timeout: 10 seconds

---

## Idempotency

All POST endpoints support idempotency via the `Idempotency-Key` header:

```bash
curl -X POST http://localhost:8080/api/tips \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"roomId": "room1", "senderId": "alice", "recipientId": "bob", "amount": 100}'
```

- First request: Returns 201 Created
- Retry with same key: Returns 200 OK with cached result
- Keys expire after 24 hours
- Automatic cleanup via scheduled job

---

## Running Tests

**Note:** Integration tests require Docker for Testcontainers.

On macOS/Linux:
```bash
./mvnw test
```

On Windows:
```bash
mvnw.cmd test
```

Current test count: **61 integration tests**

---

## Database Schema

### Tips Table (OLTP)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| room_id | VARCHAR | Room identifier (indexed) |
| sender_id | VARCHAR | Tip sender |
| recipient_id | VARCHAR | Tip recipient (indexed) |
| amount | DECIMAL(19,2) | Tip amount |
| message | VARCHAR(1000) | Optional message |
| metadata | TEXT | Optional JSON |
| created_at | TIMESTAMP | Creation time (indexed) |

### Reactions Table (OLTP)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| room_id | VARCHAR | Room identifier (indexed) |
| user_id | VARCHAR | User who reacted (indexed) |
| emoji | VARCHAR(100) | Emoji reaction |
| target_id | VARCHAR | Target content ID |
| metadata | TEXT | Optional JSON |
| created_at | TIMESTAMP | Creation time (indexed) |

### Room Stats Hourly Table (OLAP)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| room_id | VARCHAR | Room identifier |
| period_start | TIMESTAMP | Hour start (indexed with room_id) |
| period_end | TIMESTAMP | Hour end |
| total_tips | BIGINT | Tip count |
| total_amount | DECIMAL(19,2) | Sum of tips |
| unique_senders | BIGINT | Distinct tip senders |
| unique_recipients | BIGINT | Distinct tip recipients |
| average_tip_amount | DECIMAL(19,2) | Mean tip amount |
| total_reactions | BIGINT | Reaction count |
| unique_reactors | BIGINT | Distinct reactors |
| last_aggregated_at | TIMESTAMP | Aggregation time |

---

## Project Structure

```
src/
├── main/java/com/mchekin/tipcurrent/
│   ├── config/           # WebSocket configuration
│   ├── controller/       # REST controllers
│   │   ├── TipController.java
│   │   ├── ReactionController.java
│   │   ├── AnalyticsController.java
│   │   └── WebhookController.java
│   ├── domain/           # JPA entities
│   │   ├── Tip.java
│   │   ├── Reaction.java
│   │   ├── RoomStatsHourly.java
│   │   ├── WebhookEndpoint.java
│   │   └── IdempotencyRecord.java
│   ├── dto/              # Request/Response DTOs
│   ├── repository/       # Spring Data repositories
│   ├── scheduler/        # Scheduled jobs
│   └── service/          # Business logic
└── test/java/com/mchekin/tipcurrent/
    ├── TipIntegrationTest.java
    ├── ReactionIntegrationTest.java
    ├── AnalyticsIntegrationTest.java
    ├── WebhookIntegrationTest.java
    └── IdempotencyIntegrationTest.java
```

---

## Technology Stack

- Spring Boot 4.0.1
- Java 25
- PostgreSQL 17
- WebSocket with STOMP protocol
- Maven
- Lombok
- Testcontainers

---

## Future Roadmap

Planned features:

- Additional event types (Follow, Subscribe, Raid)
- Goals and Leaderboards
- Authentication and authorization
- Rate limiting
- Caching layer
- Production deployment documentation (Kubernetes, Helm)

---

## License

MIT License

## Contributing

Contributions should maintain the project's focus on clarity, correctness, and conventional Spring Boot patterns. See `.claude/VISION.md` for project principles.
