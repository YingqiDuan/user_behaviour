# User Behavior Data Collection Service

A Spring Boot microservice for collecting user behavior data and sending it to Kafka, along with a consumer service for processing the events.

## Overview

This project consists of two main components:

1. **Data Collection Service**: A REST API that collects user behavior events from various sources and sends them to Kafka topics based on event type.

2. **Event Processing Service**: A Kafka consumer that processes events from the Kafka topics, performs data enrichment, and stores them in a MySQL database.

## Requirements

- Java 17 or higher
- Maven
- Docker and Docker Compose (for running Kafka, MySQL, and other infrastructure)

## Getting Started

### 1. Start the Infrastructure

The project includes a Docker Compose file that sets up:
- Kafka
- Zookeeper
- MySQL
- Redis

Run the following command to start all services:

```bash
docker-compose up -d
```

### 2. Build the Application

Build the application with Maven:

```bash
mvn clean package
```

### 3. Run the Producer Service

To run the data collection service (producer):

```bash
java -jar target/user_behaviour-0.0.1-SNAPSHOT.jar
```

The producer service will start on port 8080.

To set port:
--server.port=8080

### 4. Run the Consumer Service

To run the event processing service (consumer):

```bash
java -jar target/user_behaviour-0.0.1-SNAPSHOT.jar --spring.profiles.active=consumer

# On Windows
run-consumer.bat

# On Unix-like systems
chmod +x run-consumer.sh
./run-consumer.sh
```

The consumer service will start on port 8081.

## Producer API Endpoints

### Standard API

#### 1. Send a Single Event

**Endpoint:** `POST /api/events`

**Request Body:**

```json
{
  "userId": "user123",
  "eventType": "PAGE_VIEW",
  "source": "web-app",
  "eventTime": "2023-07-14T12:34:56",
  "eventData": {
    "page": "/products",
    "referrer": "/home"
  },
  "sessionId": "sess-12345",
  "deviceInfo": "Chrome 114.0 on Windows 10",
  "ipAddress": "192.168.1.100"
}
```

**Response:** HTTP 202 Accepted

#### 2. Send Multiple Events (Batch)

**Endpoint:** `POST /api/events/batch`

**Request Body:**

```json
[
  {
    "userId": "user123",
    "eventType": "PAGE_VIEW",
    "source": "web-app",
    "eventData": {
      "page": "/products"
    }
  },
  {
    "userId": "user456",
    "eventType": "BUTTON_CLICK",
    "source": "mobile-app",
    "eventData": {
      "buttonId": "add-to-cart",
      "productId": "prod123"
    }
  }
]
```

**Response:** HTTP 202 Accepted

### Concise API

#### 1. Collect a Single Event

**Endpoint:** `POST /collect`

**Request Body:** Same as the standard API

**Response:** HTTP 202 Accepted with a simpler response body

#### 2. Collect Multiple Events (Batch)

**Endpoint:** `POST /collect/batch`

**Request Body:** Same as the standard API

**Response:** HTTP 202 Accepted with a simpler response body

## Consumer API Endpoints

### 1. Get Processing Statistics

**Endpoint:** `GET /api/stats`

**Response:**
```json
{
  "totalEvents": 150,
  "timestamp": "2023-07-14T15:45:30.123"
}
```

### 2. Get Event Type Statistics

**Endpoint:** `GET /api/stats/event-types/{eventType}?period={period}`

Where `{eventType}` is the type of event (e.g., PAGE_VIEW, BUTTON_CLICK) and `{period}` is hour, day, week, or month.

**Response:**
```json
{
  "eventType": "PAGE_VIEW",
  "period": "day",
  "count": 45,
  "startTime": "2023-07-13T15:45:30.123",
  "endTime": "2023-07-14T15:45:30.123"
}
```

## Event Types and Kafka Topics

Events are routed to different Kafka topics based on their `eventType` field:

| Event Type | Kafka Topic |
|------------|-------------|
| PAGE_VIEW | user-behavior-pageview |
| BUTTON_CLICK, LINK_CLICK | user-behavior-click |
| SEARCH | user-behavior-search |
| PURCHASE | user-behavior-purchase |
| Other event types | user-behavior-other |

Common event types that can be used:

- `PAGE_VIEW`: User viewed a page
- `BUTTON_CLICK`: User clicked a button
- `FORM_SUBMIT`: User submitted a form
- `PRODUCT_VIEW`: User viewed a product
- `ADD_TO_CART`: User added a product to cart
- `PURCHASE`: User made a purchase
- `LOGIN`: User logged in
- `LOGOUT`: User logged out
- `SEARCH`: User performed a search
- `NOTIFICATION_RECEIVED`: User received a notification
- `NOTIFICATION_CLICKED`: User clicked on a notification

## Architecture

### Producer Service
- Receives user behavior events through REST APIs
- Validates and enriches the events
- Routes events to different Kafka topics based on event type
- Handles critical events synchronously for guaranteed delivery

### Consumer Service
- Consumes events from multiple Kafka topics using a single consumer group
- Processes events in batches for better performance
- Performs data enrichment and validation
- Stores events in a MySQL database
- Provides statistics on processed events

## Configuration

### Producer Configuration
The producer service can be configured through `application.properties`:
- `server.port`: The port on which the application runs
- `spring.kafka.bootstrap-servers`: Kafka bootstrap server(s)
- `spring.kafka.producer.*`: Kafka producer configurations
- `user.behavior.topic.*`: Kafka topic configurations for different event types

### Consumer Configuration
The consumer service can be configured through `application-consumer.properties`:
- `server.port`: The port on which the consumer service runs
- `spring.kafka.consumer.*`: Kafka consumer configurations
- `spring.datasource.*`: Database connection settings
- `app.batch.size`: Number of events to batch before database insertion

## Monitoring

The application logs basic metrics about event processing, including:

- Total events processed
- Failed events
- Events per second
- Breakdown by event type

In a production environment, this would be replaced with more comprehensive monitoring using tools like Prometheus, Grafana, etc. 