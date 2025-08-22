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
- **H2 Database** - File-based database for persistence
- **Maven** - Build tool

### Project Structure
```
src/main/java/com/example/workflow/
├── Application.java              # Spring Boot main application class
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
Camunda automatically creates and manages database schema on startup. The H2 database file persists process instances, tasks, and history.