# Technology Stack

## Project Type
Enterprise workflow application demonstrating distributed business process management with session persistence and caching capabilities.

## Core Technologies

### Primary Language(s)
- **Language**: Java 21 (LTS)
- **Runtime**: OpenJDK 21 with G1 garbage collector optimization
- **Language-specific tools**: Maven 3.9+ for dependency management and build automation

### Key Dependencies/Libraries
- **Spring Boot 3.4.4**: Application framework with auto-configuration and production-ready features
- **Camunda BPM 7.23.0**: Business Process Management engine with BPMN 2.0 support
- **Hazelcast 5.5.0**: In-Memory Data Grid for distributed caching and session storage
- **Spring Session**: Distributed session management with Hazelcast backend
- **PostgreSQL 42.7.7**: Production database for workflow persistence
- **Spring Boot Actuator**: Production monitoring and health check endpoints
- **H2 Database**: Embedded database for testing scenarios

### Application Architecture
**Layered Architecture with Distributed Components:**
- **Presentation Layer**: Camunda web interface (Cockpit, Tasklist, Admin)
- **Business Logic Layer**: BPMN processes with custom service delegates
- **Caching Layer**: Hazelcast distributed maps for session and workflow data
- **Persistence Layer**: PostgreSQL for workflow engine data, Hazelcast for session data
- **Configuration Layer**: Spring Boot auto-configuration with externalized properties

### Data Storage
- **Primary storage**: PostgreSQL for Camunda workflow engine data (process instances, tasks, history)
- **Caching**: Hazelcast In-Memory Data Grid for session management and workflow variables
- **Data formats**: JSON for workflow variables, binary serialization for session data
- **Session persistence**: Distributed across Hazelcast cluster with configurable backup

### External Integrations
- **APIs**: Camunda REST API endpoints for workflow management
- **Protocols**: HTTP/HTTPS for REST API, Hazelcast native protocol for cluster communication
- **Authentication**: Camunda built-in authentication with configurable admin users
- **Monitoring**: Prometheus metrics export via Spring Boot Actuator

### Monitoring & Dashboard Technologies
- **Dashboard Framework**: Camunda web applications (Angular-based) with embedded Tomcat
- **Real-time Communication**: HTTP polling for workflow updates, Hazelcast events for cluster status
- **Visualization Libraries**: Camunda Cockpit for workflow visualization, BPMN.js for process diagrams
- **State Management**: Camunda engine state persistence with Hazelcast session state

## Development Environment

### Build & Development Tools
- **Build System**: Apache Maven with Spring Boot Maven plugin
- **Package Management**: Maven Central Repository with version management via BOM
- **Development workflow**: Spring Boot DevTools for hot reload, embedded server for rapid development
- **Container Development**: Docker Compose with PostgreSQL and application services

### Code Quality Tools
- **Static Analysis**: Built-in via IDE integration, Maven compiler warnings
- **Formatting**: Java standard formatting with IDE configuration
- **Testing Framework**: JUnit 5 with Spring Boot Test, Testcontainers for integration testing
- **Documentation**: Javadoc for API documentation, comprehensive README and CLAUDE.md

### Version Control & Collaboration
- **VCS**: Git with conventional commit messages
- **Branching Strategy**: Feature branching with main branch protection
- **Code Review Process**: Pull request-based code reviews

### Dashboard Development
- **Live Reload**: Spring Boot DevTools with automatic restart on code changes
- **Port Management**: Configurable ports (8080 for application, 9000 for actuator)
- **Multi-Instance Support**: Hazelcast cluster discovery for running multiple instances

## Deployment & Distribution

### Target Platform(s)
- **Primary**: Docker containers on Kubernetes or Docker Compose
- **Development**: Local development with embedded servers
- **Cloud**: Compatible with major cloud platforms (AWS, Azure, GCP)

### Distribution Method
- **Container Images**: Docker images built via Spring Boot Maven plugin
- **Configuration**: External YAML configuration files for environment-specific settings
- **Dependencies**: Self-contained JAR with embedded Tomcat server

### Installation Requirements
- **Java Runtime**: Java 21+ (OpenJDK or Oracle JDK)
- **Database**: PostgreSQL 15+ for production
- **Memory**: Minimum 1GB RAM, recommended 2GB+ for production workloads
- **Network**: Hazelcast cluster ports (5701-5801) for multi-instance deployment

### Update Mechanism
- **Rolling Updates**: Zero-downtime deployments via load balancer with health checks
- **Database Migrations**: Camunda engine handles schema migrations automatically
- **Configuration Updates**: External configuration files with application restart

## Technical Requirements & Constraints

### Performance Requirements
- **Response Time**: Sub-200ms for workflow variable operations, sub-500ms for process operations
- **Throughput**: 1000+ process instances per minute on standard hardware
- **Memory Usage**: Optimized GC settings with G1 collector for low-latency performance
- **Startup Time**: Sub-30 seconds for application initialization

### Compatibility Requirements  
- **Platform Support**: Cross-platform (Linux, Windows, macOS) via Java runtime
- **Database Versions**: PostgreSQL 12+ (production), H2 2.x (testing)
- **Java Versions**: Java 21+ with backward compatibility considerations
- **Browser Support**: Modern browsers for Camunda web interface

### Security & Compliance
- **Session Security**: Secure session IDs, configurable cookie settings (secure, http-only, same-site)
- **Database Security**: Connection pooling with credential management
- **Network Security**: Hazelcast cluster authentication and encryption support
- **Configuration Security**: Externalized secrets and environment-specific security settings

### Scalability & Reliability
- **Expected Load**: 10-100 concurrent users, 1000+ process instances per day
- **Availability Requirements**: 99.9% uptime with graceful degradation during Hazelcast failures
- **Growth Projections**: Horizontal scaling via additional application instances with shared Hazelcast cluster

## Technical Decisions & Rationale

### Decision Log
1. **Java 21 Selection**: Long-term support version with performance improvements and modern language features for enterprise development
2. **Camunda 7.23.0 vs Camunda 8**: Version 7 chosen for Spring Boot integration simplicity and mature ecosystem
3. **Hazelcast for Session Storage**: Selected over Redis for native Java integration and embedded cluster capabilities
4. **PostgreSQL over H2**: Production-grade database with ACID compliance and Camunda engine optimization
5. **Spring Boot 3.4.4**: Latest stable version with comprehensive auto-configuration and production features
6. **G1GC Configuration**: Optimized garbage collection settings for low-latency, high-throughput applications

## Known Limitations
- **Single Database**: Current implementation uses single PostgreSQL instance (could be enhanced with read replicas)
- **Hazelcast Persistence**: In-memory storage without disk persistence (sessions lost on full cluster restart)
- **Authentication**: Basic Camunda authentication (could be enhanced with LDAP/OAuth integration)
- **Monitoring**: Basic health checks (could be enhanced with custom business metrics and alerting)