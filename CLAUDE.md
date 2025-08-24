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