# TipCurrent

A production-quality, open-source engagement backend for live streaming platforms. TipCurrent handles tips and other engagement events that drive interaction and monetization in gaming, live events, webinars, and creator content.

## Overview

TipCurrent provides a reliable, self-hosted solution for ingesting and persisting engagement events from live streaming platforms. This initial version focuses on accepting tip events via HTTP and storing them durably in PostgreSQL.

## Features

- REST API for creating and querying tip events
- Real-time WebSocket broadcasting for tip events
- Production-quality Analytics API with pre-aggregated summary tables
- Scheduled hourly aggregation for OLTP/OLAP separation
- PostgreSQL persistence with proper indexing
- Docker Compose for easy local development
- Integration tests with Testcontainers
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

Expected response:

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

## API Reference

### Create Tip

**Endpoint:** `POST /api/tips`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| roomId | string | Yes | Identifier for the room/stream where the tip occurred |
| senderId | string | Yes | Identifier for the user sending the tip |
| recipientId | string | Yes | Identifier for the user receiving the tip |
| amount | decimal | Yes | Tip amount (e.g., tokens, currency units) |
| message | string | No | Optional message from sender (max 1000 chars) |
| metadata | string | No | Optional JSON metadata for additional context |

**Response:** HTTP 201 Created with the persisted tip including generated ID and timestamp.

### List Tips

**Endpoint:** `GET /api/tips`

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| roomId | string | No | Filter tips by room ID |
| senderId | string | No | Filter tips by sender ID |
| recipientId | string | No | Filter tips by recipient ID |
| page | integer | No | Page number (default: 0) |
| size | integer | No | Page size (default: 20, max: 100) |

**Response:** HTTP 200 OK with paginated list of tips, sorted by createdAt (newest first).

**Examples:**

Get all tips (paginated):
```bash
curl http://localhost:8080/api/tips
```

Get tips for a specific room:
```bash
curl http://localhost:8080/api/tips?roomId=gaming_stream_123
```

Get tips received by a user:
```bash
curl http://localhost:8080/api/tips?recipientId=bob
```

Get tips with pagination:
```bash
curl http://localhost:8080/api/tips?page=0&size=10
```

Combine filters:
```bash
curl http://localhost:8080/api/tips?roomId=gaming_stream_123&recipientId=bob
```

**Response Format:**

```json
{
  "content": [
    {
      "id": 2,
      "roomId": "gaming_stream_123",
      "senderId": "charlie",
      "recipientId": "bob",
      "amount": 200.00,
      "message": "Amazing!",
      "metadata": null,
      "createdAt": "2024-01-15T10:35:00.000Z"
    },
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
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

### Get Tip by ID

**Endpoint:** `GET /api/tips/{id}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | long | Yes | The tip ID |

**Response:** HTTP 200 OK with the tip details, or HTTP 404 Not Found if the tip doesn't exist.

**Example:**

```bash
curl http://localhost:8080/api/tips/1
```

**Response:**

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

## Real-Time Updates via WebSocket

TipCurrent provides real-time tip event broadcasting using WebSocket with STOMP protocol. 
When a tip is created via the REST API, it's automatically broadcast to all WebSocket subscribers in the same room.

### WebSocket Endpoint

Connect to: `ws://localhost:8080/ws`

### Subscription Pattern

Subscribe to room-specific topics to receive tip events:

**Topic:** `/topic/rooms/{roomId}`

Where `{roomId}` is the room identifier (e.g., `gaming_stream_123`).

### JavaScript Client Example

```javascript
// Using SockJS and STOMP.js
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);

    // Subscribe to a specific room
    stompClient.subscribe('/topic/rooms/gaming_stream_123', function(message) {
        const tip = JSON.parse(message.body);
        console.log('Received tip:', tip);
        // Handle the tip event (update UI, play sound, etc.)
    });
});
```

### Message Format

WebSocket messages contain the same TipResponse format as the REST API:

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

### Use Cases

- **Live Stream Overlays**: Display tips in real-time on stream
- **Audience Engagement**: Show tip notifications to viewers
- **Creator Dashboards**: Real-time revenue and tip tracking
- **Moderation Tools**: Monitor tip activity across rooms

### Example: Full Client Integration

```html
<!DOCTYPE html>
<html>
<head>
    <title>TipCurrent WebSocket Demo</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
</head>
<body>
    <h1>Room: gaming_stream_123</h1>
    <div id="tips"></div>

    <script>
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);

        stompClient.connect({}, function(frame) {
            console.log('Connected to TipCurrent');

            stompClient.subscribe('/topic/rooms/gaming_stream_123', function(message) {
                const tip = JSON.parse(message.body);
                displayTip(tip);
            });
        });

        function displayTip(tip) {
            const tipsDiv = document.getElementById('tips');
            const tipElement = document.createElement('div');
            tipElement.innerHTML = `
                <strong>${tip.senderId}</strong> tipped
                <strong>${tip.recipientId}</strong>
                ${tip.amount} tokens
                ${tip.message ? ': ' + tip.message : ''}
            `;
            tipsDiv.prepend(tipElement);
        }
    </script>
</body>
</html>
```

## Analytics API

TipCurrent provides production-quality analytics using **pre-aggregated summary tables**. This architecture separates OLTP (transactional writes) from OLAP (analytical queries), ensuring excellent performance for both operations.

### Architecture

The analytics system uses a scheduled aggregation pattern:

```
Tip Creation → tips table (OLTP, write-optimized)
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
- Horizontally scalable with read replicas
- Simple operations - just Postgres + Spring

**Trade-offs:**
- Data freshness: Up to 1 hour lag (analytics show stats up to the last completed hour)
- Storage overhead: Minimal (~168 rows/room/week)

### Get Room Statistics

**Endpoint:** `GET /api/analytics/rooms/{roomId}/stats`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| roomId | string | Yes | The room identifier |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| startDate | instant | No | Filter from this time (ISO 8601 format) |
| endDate | instant | No | Filter to this time (ISO 8601 format) |

**Response:** HTTP 200 OK with room statistics aggregated by hour.

**Examples:**

Get all statistics for a room:
```bash
curl http://localhost:8080/api/analytics/rooms/gaming_stream_123/stats
```

Get statistics for a specific date range:
```bash
curl "http://localhost:8080/api/analytics/rooms/gaming_stream_123/stats?startDate=2024-01-15T10:00:00Z&endDate=2024-01-15T16:00:00Z"
```

**Response Format:**

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
      "averageTipAmount": 100.00
    },
    {
      "periodStart": "2024-01-15T11:00:00Z",
      "periodEnd": "2024-01-15T12:00:00Z",
      "totalTips": 30,
      "totalAmount": 3200.00,
      "uniqueSenders": 15,
      "uniqueRecipients": 4,
      "averageTipAmount": 106.67
    }
  ],
  "summary": {
    "totalTips": 55,
    "totalAmount": 5700.00,
    "averageTipAmount": 103.64
  }
}
```

**Response Fields:**

- `roomId`: The room identifier
- `stats`: Array of hourly statistics, ordered by period start (ascending)
  - `periodStart`: Start of the hour (inclusive)
  - `periodEnd`: End of the hour (exclusive)
  - `totalTips`: Total number of tips in this hour
  - `totalAmount`: Sum of all tip amounts
  - `uniqueSenders`: Count of distinct senders
  - `uniqueRecipients`: Count of distinct recipients
  - `averageTipAmount`: Mean tip amount for this hour
- `summary`: Aggregated statistics across all returned periods
  - `totalTips`: Total tips across all periods
  - `totalAmount`: Sum across all periods
  - `averageTipAmount`: Overall average (calculated from summary totals)

### How Aggregation Works

1. **Scheduled Job**: Every hour at :05 (e.g., 10:05, 11:05), a background job runs
2. **Single Query**: One efficient `GROUP BY` query aggregates all tips from the previous hour across all rooms
3. **Summary Table**: Results are stored in the `room_stats_hourly` table
4. **Analytics Queries**: The `/api/analytics` endpoint reads ONLY from the summary table, never from the `tips` table

### Data Freshness

Analytics data is aggregated hourly with up to **1 hour lag**:
- Tips created at 10:30 will be aggregated at 11:05
- The 10:00-11:00 hourly stats become available at 11:05

This trade-off ensures production-quality performance and scalability.

### Use Cases

- **Creator Dashboards**: Track revenue trends over time
- **Performance Analytics**: Compare engagement across different time periods
- **Audience Insights**: Analyze sender and recipient patterns
- **Historical Reporting**: Generate reports on past engagement

### Future Evolution

The API is designed to support migration to dedicated analytics databases (ClickHouse, TimescaleDB) without breaking changes:
- API contract remains identical
- Backend implementation swaps data source
- Clients see no difference

## Running Tests

### Unit and Integration Tests

**Note:** Integration tests require Docker to be running for Testcontainers.

On macOS/Linux:
```bash
./mvnw test
```

On Windows:
```bash
mvnw.cmd test
```

The integration tests use Testcontainers to spin up a real PostgreSQL instance, ensuring tests run against the actual database.

### Manual Testing

You can use the included Docker Compose setup to test manually:

1. Start PostgreSQL:
   ```bash
   docker-compose up -d
   ```

2. Run the application (macOS/Linux):
   ```bash
   ./mvnw spring-boot:run
   ```

   Or on Windows:
   ```bash
   mvnw.cmd spring-boot:run
   ```

3. Send requests using curl, Postman, or your preferred HTTP client

## Database Schema

### Tips Table (OLTP - Write-Optimized)

The `tips` table stores transactional tip events:

- `id`: Auto-generated primary key
- `room_id`: Indexed for efficient room-based queries
- `sender_id`: User who sent the tip
- `recipient_id`: User who received the tip (indexed)
- `amount`: Decimal value with precision 19, scale 2
- `message`: Optional text message (up to 1000 characters)
- `metadata`: Optional JSON metadata
- `created_at`: Timestamp, auto-set on creation (indexed)

### Room Stats Hourly Table (OLAP - Read-Optimized)

The `room_stats_hourly` table stores pre-aggregated analytics:

- `id`: Auto-generated primary key
- `room_id`: Room identifier
- `period_start`: Start of hourly period (indexed with room_id)
- `period_end`: End of hourly period
- `total_tips`: Count of tips in this hour
- `total_amount`: Sum of tip amounts (precision 19, scale 2)
- `unique_senders`: Count of distinct senders
- `unique_recipients`: Count of distinct recipients
- `average_tip_amount`: Mean tip amount (precision 19, scale 2)
- `last_aggregated_at`: Timestamp when aggregation ran

**Indexes:**
- Composite index on `(room_id, period_start)` for efficient range queries
- Index on `period_start` for time-based queries
- Unique constraint on `(room_id, period_start)` prevents duplicate aggregations

## Project Structure

```
src/
├── main/
│   ├── java/com/mchekin/tipcurrent/
│   │   ├── config/           # WebSocket configuration
│   │   ├── controller/       # REST controllers (Tip, Analytics)
│   │   ├── domain/           # JPA entities (Tip, RoomStatsHourly)
│   │   ├── dto/              # Request/Response DTOs
│   │   ├── repository/       # Spring Data repositories
│   │   ├── scheduler/        # Scheduled jobs (hourly aggregation)
│   │   └── service/          # Business logic (stats aggregation)
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/mchekin/tipcurrent/
        ├── TipIntegrationTest.java
        └── AnalyticsIntegrationTest.java
```

## Configuration

Key configuration in `application.properties`:

- Database URL, username, password
- JPA/Hibernate settings
- Server port (default: 8080)

For local development, defaults match the Docker Compose configuration.

## Technology Stack

- Spring Boot 4.0.1
- Java 25
- PostgreSQL 17
- WebSocket with STOMP protocol
- Maven
- Lombok
- Testcontainers

## Future Roadmap

Future iterations may include:

- Aggregation and analytics
- Caching layer
- Authentication and authorization
- Rate limiting
- Multi-region deployment support

## License

MIT License

## Contributing

This is the initial version focusing on the core write path. Contributions should maintain the project's focus on clarity, correctness, and conventional Spring Boot patterns.
