# Camunda BPM Hazelcast Integration

This project demonstrates the integration of Camunda BPM with Hazelcast In-Memory Data Grid (IMDG) for distributed workflow variable storage, retrieval, and session management.

## Overview

The project showcases how to use Hazelcast as a distributed cache for:
- Storing and retrieving workflow variables in Camunda BPM processes
- Distributed session management with Spring Session
- Horizontal scaling and session persistence across application restarts

It includes service delegates that interact with Hazelcast maps and Spring Session configuration for distributed session storage.

## Technology Stack

- **Camunda BPM 7.23.0** - Workflow engine
- **Hazelcast 5.5.0** - In-Memory Data Grid
- **Spring Boot 3.4.4** - Application framework
- **Spring Session** - Distributed session management
- **PostgreSQL 17** - Production database
- **H2 Database** - Testing database
- **Docker & Docker Compose** - Containerization
- **Java 21** - Programming language
- **Maven** - Build tool

## Architecture

### Core Components

- **HazelcastAutoConfiguration**: Auto-configures Hazelcast instance with Spring Boot
- **SessionConfig**: Spring Session configuration for Hazelcast-based session storage
- **putServiceDelegate**: Service delegate for storing data to Hazelcast maps
- **getServiceDelegate**: Service delegate for retrieving data from Hazelcast maps
- **HazelcastHealthIndicator**: Health check endpoint for Hazelcast status
- **HazelcastProperties**: Configuration properties for Hazelcast and session settings
- **Integration Tests**: Comprehensive test suite for Hazelcast and session integration

### Service Delegates

#### putServiceDelegate
- Stores workflow variables to Hazelcast distributed maps
- Key format: `{processInstanceId}_{activityId}`
- Supports error handling with BPMN error propagation

#### getServiceDelegate
- Retrieves workflow variables from Hazelcast distributed maps
- Returns data back to process context
- Handles missing keys gracefully

### Session Management

The application includes Spring Session integration with Hazelcast for distributed session management:

#### Features
- **Horizontal Scaling**: Sessions are shared across multiple application instances
- **Session Persistence**: Sessions survive application restarts
- **Secure Configuration**: HTTP-only, secure cookies with CSRF protection
- **Configurable Timeouts**: Environment-specific session timeout settings

#### Session Configuration
Sessions are stored in Hazelcast using the `spring-session-sessions` map with configurable properties for different environments (development, production, test).

#### Benefits for Camunda
- User authentication state preserved across server restarts
- Load balancing support for multiple Camunda instances
- Improved user experience with persistent login sessions

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Maven (for local development)

### Running with Docker Compose

1. **Start all services**:
```bash
docker-compose up -d
```

2. **Access Camunda Web Interface**:
   - URL: http://localhost:8080
   - Username: `demo`
   - Password: `demo`

3. **Access Components**:
   - **Camunda Web Apps**: http://localhost:8080/camunda/
   - **REST API**: http://localhost:8080/engine-rest/
   - **Health Check**: http://localhost:8080/actuator/health
   - **PostgreSQL**: localhost:5432 (username: `postgres`, password: `password`)

### Local Development

1. **Start PostgreSQL** (or use H2 for testing):
```bash
docker-compose up -d postgresql
```

2. **Run the application**:
```bash
mvn spring-boot:run
```

3. **Run tests**:
```bash
mvn test
```

## API Testing

### Process Definition Tests

Test the Hazelcast integration using the Camunda REST API:

#### 1. Start Parallel Process (Store Data)
```bash
curl --location 'http://localhost:8080/engine-rest/process-definition/key/process/start' \
--header 'Content-Type: application/json' \
--data '{
  "businessKey": "123"
}'
```

This will:
- Start a process instance with business key "123"
- Use `putServiceDelegate` to store data in Hazelcast
- Store key-value pair: `{processInstanceId}_rest-api` → process data

#### 2. Start Get Process (Retrieve Data)
```bash
curl --location 'http://localhost:8080/engine-rest/process-definition/key/getprocess/start' \
--header 'Content-Type: application/json' \
--data '{
  "businessKey": "456",
  "variables": {
      "hazelcast_key": {"value": "LOOK_FOR_YOUR_CAMUNDA_CONSOLE"}
  }
}'
```

This will:
- Start a process instance with business key "456"
- Use `getServiceDelegate` to retrieve data from Hazelcast
- Look for data with the specified key
- Return retrieved data as process variables

### Additional REST API Examples

#### Get Process Instances
```bash
curl --location 'http://localhost:8080/engine-rest/process-instance'
```

#### Get Process Variables
```bash
curl --location 'http://localhost:8080/engine-rest/process-instance/{processInstanceId}/variables'
```

#### Check Hazelcast Health
```bash
curl --location 'http://localhost:8080/actuator/health/hazelcast'
```

## Configuration

### Production Configuration (`application.yaml`)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgresql:5432/camunda
    username: postgres
    password: password

hazelcast:
  enabled: true
  instance-name: camunda-hazelcast
  map:
    name: myMap
    backup-count: 1
    time-to-live-seconds: 3600

spring:
  session:
    store-type: hazelcast
    hazelcast:
      map-name: "spring-session-sessions"
      flush-mode: on-save
      save-mode: on-set-attribute
    timeout: 30m
  servlet:
    session:
      cookie:
        name: "CAMUNDA_SESSION"
        secure: true
        http-only: true
        same-site: lax
```

### Test Configuration (`application-test.yaml`)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC

camunda:
  bpm:
    database:
      type: h2
```

## Development

### Building

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package
mvn package

# Clean
mvn clean

# Local run with "dev" profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Testing

The project includes comprehensive integration tests:

- **HazelcastWorkflowIntegrationTest**: Tests Hazelcast integration
  - Instance injection validation
  - Data storage and retrieval
  - Process instance data isolation
  - Map configuration verification

- **SessionConfigurationTest**: Tests Spring Session configuration
  - SessionRepository bean registration
  - Session timeout and cookie configuration
  - Hazelcast session map integration

- **SessionIntegrationTest**: Tests complete session lifecycle
  - Session creation, storage, and retrieval
  - Session sharing between instances
  - Session persistence across restarts

- **CamundaSessionE2ETest**: End-to-end session testing
  - Camunda authentication integration
  - Web interface session management
  - Multi-user session isolation

Run specific tests:
```bash
mvn test -Dtest=HazelcastWorkflowIntegrationTest
```

### Database Configuration

#### Production (PostgreSQL)
- Database: `camunda`
- Host: `postgresql:5432` (Docker) or `localhost:5432` (local)
- Schema: Automatically created by Camunda

#### Testing (H2)
- In-memory database with PostgreSQL compatibility mode
- Schema: Automatically created during tests
- No persistence between test runs

## Monitoring

### Health Checks

The application provides health check endpoints:

- **Application Health**: `/actuator/health`
- **Hazelcast Health**: Included in main health check
- **Database Health**: Included in main health check

### Hazelcast Monitoring

Monitor Hazelcast cluster status:
- Cluster size and member information
- Map statistics and data distribution
- Performance metrics

## Troubleshooting

### Common Issues

1. **Database Connection Issues**:
   - Ensure PostgreSQL is running and accessible
   - Check connection credentials in `application.yaml`

2. **Hazelcast Connection Issues**:
   - Verify Hazelcast configuration in `HazelcastAutoConfiguration`
   - Check network connectivity between services

3. **Proxy Issues**:
   - Ensure webproxy service is running
   - Check `webproxy/proxy.conf` configuration

### Logs

View application logs:
```bash
# Docker logs
docker-compose logs camunda

# Local development
mvn spring-boot:run
```

## Service Architecture

### Docker Services

- **camunda**: Main application with Hazelcast integration
- **postgresql**: PostgreSQL database for production
- **webproxy**: Nginx reverse proxy for web access

### Network Configuration

All services communicate through the `proxy` Docker network, enabling service discovery and inter-container communication.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes and add tests
4. Ensure all tests pass: `mvn test`
5. Submit a pull request

## License

This project is for demonstration purposes. Please refer to individual component licenses:
- Camunda BPM: Enterprise license required for production use
- Hazelcast: Apache License 2.0
- Spring Boot: Apache License 2.0