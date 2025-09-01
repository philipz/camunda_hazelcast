# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Camunda BPM Spring Boot application that demonstrates workflow automation using Camunda 7.23.0. The application includes a simple BPMN process with a user task and uses H2 database for persistence.

## Development Commands

### Build and Run
```bash
# Build the project
mvn compile

# Run the application
mvn spring-boot:run

# Package as JAR
mvn package

# Clean build artifacts
mvn clean
```

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ClassName

# Run tests with specific profile
mvn test -Dspring.profiles.active=test
```

### Database
The application uses H2 file database located at `./camunda-h2-database.mv.db`. The database is automatically created when the application starts.

## Architecture

### Technology Stack
- **Spring Boot 3.4.4** - Application framework
- **Camunda BPM 7.23.0** - Workflow engine
- **Java 21** - Programming language
- **PostgreSQL** - Database for persistence
- **Hazelcast** - Distributed caching and session storage
- **Spring Session** - Distributed session management
- **Maven** - Build tool

### Project Structure
```
src/main/java/com/example/workflow/
├── Application.java              # Spring Boot main application class
├── config/
│   ├── HazelcastAutoConfiguration.java  # Hazelcast configuration
│   ├── HazelcastProperties.java         # Configuration properties
│   ├── SessionConfig.java               # Spring Session configuration
│   └── HazelcastHealthIndicator.java    # Health check
├── tasks/
│   ├── getServiceDelegate.java         # Hazelcast data retrieval
│   └── putServiceDelegate.java         # Hazelcast data storage
src/main/resources/
├── application.yaml              # Application configuration
└── process.bpmn                  # BPMN workflow definition
```

### Key Components

#### Application Configuration
- **Database**: H2 file database with JDBC URL `jdbc:h2:file:./camunda-h2-database`
- **Admin User**: Default admin user `demo/demo` for Camunda web interface
- **History**: Process history retention set to 180 days

#### BPMN Process
- **Process ID**: `my-project-process`
- **Simple workflow**: Start Event → User Task ("Say hello to demo") → End Event
- **Task Assignment**: Assigned to user `demo`

### Camunda Integration
The application uses Camunda Spring Boot starters:
- `camunda-bpm-spring-boot-starter-rest` - REST API endpoints
- `camunda-bpm-spring-boot-starter-webapp` - Web interface (Cockpit, Tasklist, Admin)
- `camunda-engine-plugin-spin` - JSON/XML data processing
- `camunda-spin-dataformat-all` - Data format support

### Development Notes

#### Accessing the Application
- **Camunda Web Interface**: http://localhost:8080 (login: demo/demo)
- **REST API**: http://localhost:8080/engine-rest/
- **H2 Console**: Available if enabled in configuration

#### BPMN Process Development
- Process definition is in `src/main/resources/process.bpmn`
- Process is automatically deployed on application startup
- History time-to-live is set to 180 days for process instances

#### Adding New Service Tasks
When adding service tasks to BPMN processes:
1. Create Java delegate classes implementing `JavaDelegate`
2. Register beans in Spring context
3. Reference in BPMN using `camunda:delegateExpression="#{serviceName}"`

#### Database Schema
Camunda automatically creates and manages database schema on startup. The PostgreSQL database persists process instances, tasks, and history.

## Session Management

### Spring Session with Hazelcast

The application uses Spring Session with Hazelcast for distributed session management, enabling:
- Horizontal scaling across multiple application instances
- Session persistence across application restarts
- Shared session state in clustered deployments

### Session Configuration

#### Basic Configuration
```yaml
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

#### Environment-Specific Settings

**Development Configuration** (`application-dev.yaml`):
```yaml
spring:
  session:
    timeout: 60m  # Longer timeout for development
  servlet:
    session:
      cookie:
        secure: false  # Allow HTTP in development
        same-site: lax

hazelcast:
  session:
    max-inactive-interval-minutes: 60
    cookie-secure: false
```

**Production Configuration** (`application-prod.yaml`):
```yaml
spring:
  session:
    timeout: 30m  # Standard timeout
  servlet:
    session:
      cookie:
        secure: true   # HTTPS only
        same-site: strict  # Enhanced security

hazelcast:
  session:
    max-inactive-interval-minutes: 30
    cookie-secure: true
    cookie-http-only: true
```

**Test Configuration** (`application-test.yaml`):
```yaml
spring:
  session:
    timeout: 5m   # Short timeout for testing
  servlet:
    session:
      cookie:
        secure: false  # Allow HTTP in tests

hazelcast:
  session:
    max-inactive-interval-minutes: 5
    cookie-secure: false
```

### Configuration Properties

#### Session Properties
| Property | Default | Description |
|----------|---------|-------------|
| `spring.session.timeout` | 30m | Session timeout duration |
| `spring.session.hazelcast.map-name` | spring-session-sessions | Hazelcast map name for sessions |
| `spring.session.hazelcast.flush-mode` | on-save | When to flush changes to store |
| `spring.session.hazelcast.save-mode` | on-set-attribute | When to save session changes |

#### Cookie Properties
| Property | Default | Description |
|----------|---------|-------------|
| `spring.servlet.session.cookie.name` | CAMUNDA_SESSION | Session cookie name |
| `spring.servlet.session.cookie.secure` | true | HTTPS-only cookie |
| `spring.servlet.session.cookie.http-only` | true | Prevent JavaScript access |
| `spring.servlet.session.cookie.same-site` | lax | CSRF protection level |

#### Hazelcast Session Properties
| Property | Default | Description |
|----------|---------|-------------|
| `hazelcast.session.map-name` | spring-session-sessions | Session map name |
| `hazelcast.session.max-inactive-interval-minutes` | 30 | Session timeout in minutes |
| `hazelcast.session.cookie-name` | CAMUNDA_SESSION | Session cookie name |
| `hazelcast.session.cookie-secure` | true | Secure cookie flag |
| `hazelcast.session.cookie-http-only` | true | HTTP-only cookie flag |

### Session Monitoring

#### Health Check
The application includes session health monitoring:
- Session store connectivity verification
- Active session count metrics
- Session map health indicators

Access health information at: `http://localhost:9000/actuator/health`

#### Startup Verification
The application automatically verifies session configuration on startup:
- SessionRepository bean registration
- Hazelcast session map configuration
- Session storage connectivity

### Troubleshooting

#### Common Issues
1. **Sessions not persisting**: Check Hazelcast connectivity and map configuration
2. **Cookie not set**: Verify cookie domain and security settings
3. **Session timeout too short**: Adjust timeout configuration for your use case
4. **Multiple instances not sharing sessions**: Ensure all instances use the same Hazelcast cluster

#### Debug Configuration
Enable session debugging:
```yaml
logging:
  level:
    org.springframework.session: DEBUG
    com.hazelcast: DEBUG
    com.example.workflow.config: DEBUG
```

### Integration with Camunda

#### Authentication Flow
1. User accesses Camunda web interface
2. Spring Security creates session
3. Session stored in Hazelcast via Spring Session
4. Session shared across all application instances
5. User remains authenticated across server restarts

#### Session Attributes
The application stores these session attributes:
- `authenticated`: User authentication status
- `username`: Authenticated username
- `authorities`: User roles and permissions
- `loginTime`: Authentication timestamp

### Performance Considerations

#### Session Size
- Keep session attributes minimal
- Avoid storing large objects in sessions
- Use references to external storage for large data

#### Hazelcast Optimization
- Configure appropriate backup count
- Set reasonable TTL for session data
- Monitor memory usage and GC impact

### Security Features

#### Session Security
- Cryptographically secure session IDs
- Session fixation protection
- Secure cookie configuration
- Configurable session timeout

#### Best Practices
1. Use HTTPS in production (`cookie.secure=true`)
2. Enable HTTP-only cookies (`cookie.http-only=true`)
3. Configure appropriate same-site policy
4. Set reasonable session timeouts
5. Monitor session metrics and health

## Distributed Transactions

### Overview

The application provides comprehensive distributed transaction support through Hazelcast's distributed transaction capabilities, enabling ACID properties across multiple service operations within Camunda workflows. This ensures data consistency and reliability in complex business processes involving multiple external services and distributed data operations.

### Key Features

- **Multi-Service Transaction Coordination**: Coordinate transactions across multiple service delegates and external API calls
- **RestServiceDelegate Transaction Integration**: Automatic transaction wrapping for REST API calls with Hazelcast storage
- **Transactional Data Structure Access**: ACID-compliant access to Hazelcast maps and queues
- **Automatic Rollback**: Comprehensive error handling with automatic transaction rollback
- **Performance Monitoring**: Built-in transaction metrics and performance tracking
- **Configurable Transaction Types**: Support for distributed, local, and XA transactions

### Architecture

#### Transaction Components

The transaction system consists of several key components:

```
src/main/java/com/example/workflow/
├── config/
│   ├── HazelcastAutoConfiguration.java  # Transaction manager configuration
│   └── HazelcastProperties.java         # Transaction properties
├── tasks/
│   └── RestServiceDelegate.java         # Transactional REST service delegate
└── transaction/
    ├── HazelcastTransactionManager.java # Core transaction management
    ├── TransactionalServiceDelegate.java # Base class for transactional delegates
    ├── TransactionContext.java          # Transaction context and metadata
    ├── TransactionUtils.java            # Transaction utility methods
    └── TransactionStatus.java           # Transaction status enumeration
```

#### Transaction Flow

1. **Transaction Begin**: Service delegate initiates transaction with configurable options
2. **Business Logic Execution**: Execute business operations within transaction context
3. **API Coordination**: External REST calls coordinated with Hazelcast operations
4. **Automatic Commit/Rollback**: Transaction automatically committed on success or rolled back on failure
5. **Metrics Collection**: Performance and outcome metrics collected for monitoring

### Configuration

#### Basic Transaction Configuration

```yaml
hazelcast:
  transaction:
    enabled: true
    timeout-seconds: 30
    type: DISTRIBUTED  # DISTRIBUTED, LOCAL, XA
    isolation: READ_COMMITTED
    max-retry-attempts: 3
    xa-enabled: false
```

#### Environment-Specific Transaction Settings

**Development Configuration** (`application-dev.yaml`):
```yaml
hazelcast:
  transaction:
    timeout-seconds: 60  # Longer timeout for debugging
    type: DISTRIBUTED
    isolation: READ_COMMITTED
    max-retry-attempts: 5
    logging:
      enabled: true
      level: DEBUG
```

**Production Configuration** (`application-prod.yaml`):
```yaml
hazelcast:
  transaction:
    timeout-seconds: 30
    type: DISTRIBUTED
    isolation: READ_COMMITTED
    max-retry-attempts: 3
    xa-enabled: true  # Enable XA for enterprise integration
    monitoring:
      metrics-enabled: true
      performance-tracking: true
```

**Test Configuration** (`application-test.yaml`):
```yaml
hazelcast:
  transaction:
    timeout-seconds: 10  # Short timeout for fast tests
    type: LOCAL  # Use local transactions for testing
    isolation: READ_COMMITTED
    max-retry-attempts: 1
```

### Usage Patterns

#### 1. Basic Transactional Service Delegate

Create service delegates by extending `TransactionalServiceDelegate`:

```java
@Component("myTransactionalService")
public class MyTransactionalService extends TransactionalServiceDelegate {
    
    @Override
    protected void executeInTransaction(DelegateExecution execution, 
                                       TransactionContext transactionContext) throws Exception {
        // Get transactional map
        TransactionalMap<String, Object> dataMap = 
            getTransactionalMap("business-data", transactionContext);
        
        // Execute business logic within transaction
        String processId = execution.getProcessInstanceId();
        String key = "process-" + processId;
        
        // Atomic operations
        Object existingData = dataMap.getForUpdate(key);  // Pessimistic locking
        BusinessData updatedData = processBusinessLogic(existingData);
        dataMap.put(key, updatedData);
        
        // Transaction automatically committed or rolled back
    }
}
```

#### 2. RestServiceDelegate with Transaction Integration

The enhanced `RestServiceDelegate` automatically coordinates REST API calls with Hazelcast operations:

```java
// BPMN Service Task Configuration
<serviceTask id="apiCall" name="Call External API" 
             camunda:delegateExpression="#{restServiceDelegate}">
  <extensionElements>
    <camunda:inputOutput>
      <camunda:inputParameter name="apiUrl">https://api.example.com/data</camunda:inputParameter>
      <camunda:inputParameter name="requestPayload">{"key": "value"}</camunda:inputParameter>
    </camunda:inputOutput>
  </extensionElements>
</serviceTask>
```

**Key Features**:
- Automatic transaction wrapping for REST calls
- Transactional storage of API response data in Hazelcast
- Coordinated rollback of both API effects and Hazelcast changes
- Support for timeout handling and retry logic
- Response data available in subsequent workflow steps

#### 3. Advanced Transaction Usage with Callback Pattern

For complex scenarios, use the callback pattern:

```java
@Component("complexTransactionalService")
public class ComplexTransactionalService extends TransactionalServiceDelegate {
    
    @Override
    protected void executeInTransaction(DelegateExecution execution, 
                                       TransactionContext transactionContext) throws Exception {
        
        // Execute multiple operations within same transaction
        String result = executeInTransaction(execution, (exec, ctx) -> {
            // Operation 1: Update inventory
            updateInventory(exec, ctx);
            
            // Operation 2: Process payment
            String paymentResult = processPayment(exec, ctx);
            
            // Operation 3: Update order status
            updateOrderStatus(exec, ctx, paymentResult);
            
            return paymentResult;
        });
        
        // Store final result
        execution.setVariable("transactionResult", result);
    }
}
```

#### 4. Multi-Service Transaction Coordination

Coordinate transactions across multiple service delegates:

```java
// Process variable to track distributed transaction
execution.setVariable("distributedTransactionId", UUID.randomUUID().toString());

// Each service delegate can participate in the same distributed transaction
// by using the shared transaction ID and coordinating through Hazelcast
```

### Transactional Data Structures

#### TransactionalMap Operations

```java
// Get transactional map within transaction context
TransactionalMap<String, BusinessData> map = 
    getTransactionalMap("business-data", transactionContext);

// Optimistic read
BusinessData data = map.get(key);

// Pessimistic read with lock
BusinessData lockedData = map.getForUpdate(key);  // Locks until transaction end

// Atomic operations
map.put(key, updatedData);
map.putIfAbsent(key, newData);
BusinessData removedData = map.remove(key);

// Conditional operations
boolean success = map.replace(key, expectedValue, newValue);
BusinessData replaced = map.replace(key, newValue);
```

#### TransactionalQueue Operations

```java
// Get transactional queue
TransactionalQueue<WorkItem> queue = 
    getTransactionalQueue("work-queue", transactionContext);

// Queue operations within transaction
queue.offer(newWorkItem);
WorkItem item = queue.poll();  // Returns null if empty
WorkItem peeked = queue.peek(); // Non-destructive read
int size = queue.size();
```

### Configuration Properties

#### Transaction Properties

| Property | Default | Description |
|----------|---------|-------------|
| `hazelcast.transaction.enabled` | true | Enable transaction support |
| `hazelcast.transaction.timeout-seconds` | 30 | Transaction timeout in seconds |
| `hazelcast.transaction.type` | DISTRIBUTED | Transaction type (DISTRIBUTED, LOCAL, XA) |
| `hazelcast.transaction.isolation` | READ_COMMITTED | Isolation level |
| `hazelcast.transaction.max-retry-attempts` | 3 | Maximum retry attempts on failure |
| `hazelcast.transaction.xa-enabled` | false | Enable XA transaction support |

#### Performance Properties

| Property | Default | Description |
|----------|---------|-------------|
| `hazelcast.transaction.monitoring.metrics-enabled` | true | Enable transaction metrics |
| `hazelcast.transaction.monitoring.performance-tracking` | true | Track performance metrics |
| `hazelcast.transaction.logging.enabled` | false | Enable transaction logging |
| `hazelcast.transaction.logging.level` | INFO | Transaction logging level |

### Performance Considerations

#### Performance Requirements

The transaction system is designed to meet these performance targets:
- **Transaction Latency**: < 200ms for simple operations
- **Throughput**: > 100 transactions/second per node
- **Memory Usage**: < 50MB per 1000 active transactions
- **Recovery Time**: < 5 seconds for failed transaction cleanup

#### Optimization Guidelines

1. **Use Appropriate Transaction Types**:
   - **LOCAL**: For single-node operations (fastest)
   - **DISTRIBUTED**: For multi-node consistency (recommended)
   - **XA**: Only when required for external XA resource coordination

2. **Minimize Transaction Scope**:
   - Keep transaction duration short (< 5 seconds recommended)
   - Avoid long-running operations within transactions
   - Use optimistic locking when possible

3. **Configure Appropriate Timeouts**:
   - Development: 60 seconds (debugging)
   - Production: 30 seconds (standard)
   - Testing: 10 seconds (fast feedback)

4. **Monitor Transaction Metrics**:
   - Active transaction count
   - Transaction success/failure rates
   - Average transaction duration
   - Rollback frequency

### Error Handling and Recovery

#### Automatic Error Handling

The transaction system provides comprehensive automatic error handling:

1. **Automatic Rollback**: Any exception in transaction logic triggers automatic rollback
2. **Retry Logic**: Failed transactions automatically retried based on configuration
3. **Timeout Handling**: Transactions exceeding timeout limits are automatically rolled back
4. **Deadlock Detection**: Automatic deadlock detection with intelligent retry

#### Error Types and Recovery

**Transaction Timeout**:
```
ERROR: Transaction timeout after 30 seconds
Recovery: Automatic rollback, retry if configured
Action: Check for long-running operations, optimize business logic
```

**Network Partition**:
```
ERROR: Hazelcast cluster partition detected
Recovery: Transaction suspended until partition heals
Action: Monitor cluster health, ensure proper network connectivity
```

**API Call Failure**:
```
ERROR: REST API call failed within transaction
Recovery: Automatic rollback of entire transaction including Hazelcast changes
Action: Check external service availability, verify API endpoints
```

**Deadlock Detection**:
```
ERROR: Potential deadlock detected between transactions
Recovery: Automatic rollback of younger transaction, retry after delay
Action: Review locking patterns, optimize transaction ordering
```

#### Manual Recovery

For manual intervention scenarios:

```bash
# Check active transactions
curl http://localhost:9000/actuator/metrics/hazelcast.transaction.active

# Monitor transaction health
curl http://localhost:9000/actuator/health/hazelcast-transactions

# View transaction logs
tail -f logs/application.log | grep "Transaction"
```

### Troubleshooting

#### Common Issues

1. **Transactions Not Starting**:
   - Verify `hazelcast.transaction.enabled=true`
   - Check HazelcastTransactionManager bean registration
   - Ensure Hazelcast instance is available

2. **Transaction Timeouts**:
   - Increase `hazelcast.transaction.timeout-seconds`
   - Optimize business logic within transactions
   - Check for external service delays

3. **High Rollback Rate**:
   - Review error handling in business logic
   - Check external API reliability
   - Verify network stability

4. **Poor Performance**:
   - Monitor transaction duration metrics
   - Consider using LOCAL transactions for single-node operations
   - Optimize data access patterns
   - Review backup count configuration

#### Debug Configuration

Enable comprehensive transaction debugging:

```yaml
logging:
  level:
    com.example.workflow.transaction: DEBUG
    com.hazelcast.transaction: DEBUG
    org.springframework.transaction: DEBUG
    
hazelcast:
  transaction:
    logging:
      enabled: true
      level: DEBUG
      include-stack-traces: true
```

#### Health Checks

Monitor transaction health through Spring Boot Actuator endpoints:

- **Transaction Health**: `GET /actuator/health/hazelcast-transactions`
- **Transaction Metrics**: `GET /actuator/metrics/hazelcast.transaction.*`
- **Active Transactions**: `GET /actuator/metrics/hazelcast.transaction.active`

### Integration with Camunda

#### Workflow Integration

Transactions integrate seamlessly with Camunda workflows:

1. **Process-Level Coordination**: Each process instance can maintain transaction state
2. **Activity-Level Transactions**: Individual service tasks execute within transactions
3. **Multi-Instance Support**: Parallel multi-instance activities support coordinated transactions
4. **Error Boundary Events**: Transaction failures can trigger BPMN error events
5. **Compensation Handling**: Support for saga pattern with compensation activities

#### Variable Management

Transaction-related variables are automatically managed:

- `transactionId`: Unique transaction identifier
- `transactionResult`: SUCCESS/FAILED status
- `transactionDuration`: Execution time in milliseconds
- `transactionRollback`: Rollback status if applicable

#### BPMN Configuration Examples

**Simple Transactional Service Task**:
```xml
<serviceTask id="dataUpdate" name="Update Business Data" 
             camunda:delegateExpression="#{myTransactionalService}" />
```

**REST API with Transaction**:
```xml
<serviceTask id="apiCall" name="External API Call" 
             camunda:delegateExpression="#{restServiceDelegate}">
  <extensionElements>
    <camunda:inputOutput>
      <camunda:inputParameter name="apiUrl">https://api.example.com/data</camunda:inputParameter>
      <camunda:inputParameter name="requestPayload">{"action": "update"}</camunda:inputParameter>
    </camunda:inputOutput>
  </extensionElements>
</serviceTask>
```

**Error Handling**:
```xml
<boundaryEvent id="transactionError" attachedToRef="dataUpdate">
  <errorEventDefinition errorRef="transactionFailure" />
</boundaryEvent>
```

### Best Practices

#### Transaction Design

1. **Keep Transactions Short**: Aim for < 5 second duration
2. **Use Appropriate Isolation Levels**: READ_COMMITTED for most cases
3. **Handle Retries Gracefully**: Design idempotent operations
4. **Plan for Rollback**: Ensure operations can be safely undone
5. **Monitor Performance**: Track transaction metrics continuously

#### Error Handling

1. **Provide Clear Error Messages**: Include transaction ID in error logs
2. **Use Proper Exception Types**: Distinguish business vs. technical failures
3. **Log Transaction Context**: Include process and activity information
4. **Implement Circuit Breakers**: Prevent cascade failures in external API calls

#### Monitoring and Alerting

1. **Set Up Alerts**: Monitor transaction failure rates
2. **Track Performance Trends**: Watch for degrading transaction performance
3. **Monitor Resource Usage**: Keep track of memory and CPU usage
4. **Review Transaction Logs**: Regular analysis of transaction patterns

This distributed transaction system provides a robust foundation for building reliable, scalable workflow applications that maintain data consistency across distributed operations while integrating seamlessly with Camunda BPM workflows.