# TipCurrent

A production-quality, open-source engagement backend for live streaming platforms. TipCurrent handles tips and other engagement events that drive interaction and monetization in gaming, live events, webinars, and creator content.

## Overview

TipCurrent provides a reliable, self-hosted solution for ingesting and persisting engagement events from live streaming platforms. This initial version focuses on accepting tip events via HTTP and storing them durably in PostgreSQL.

## Features

- REST API for creating tip events
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

The `tips` table includes:

- `id`: Auto-generated primary key
- `room_id`: Indexed for efficient room-based queries
- `sender_id`: User who sent the tip
- `recipient_id`: User who received the tip (indexed)
- `amount`: Decimal value with precision 19, scale 2
- `message`: Optional text message (up to 1000 characters)
- `metadata`: Optional JSON metadata
- `created_at`: Timestamp, auto-set on creation (indexed)

## Project Structure

```
src/
├── main/
│   ├── java/com/mchekin/tipcurrent/
│   │   ├── controller/       # REST controllers
│   │   ├── domain/           # JPA entities
│   │   ├── dto/              # Request/Response DTOs
│   │   └── repository/       # Spring Data repositories
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/mchekin/tipcurrent/
        └── TipIntegrationTest.java
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
- Maven
- Lombok
- Testcontainers

## Future Roadmap

Future iterations may include:

- Querying and filtering tips
- Aggregation and analytics
- Real-time delivery mechanisms
- Caching layer
- Authentication and authorization
- Rate limiting
- WebSocket support for real-time updates

## License

MIT License

## Contributing

This is the initial version focusing on the core write path. Contributions should maintain the project's focus on clarity, correctness, and conventional Spring Boot patterns.
