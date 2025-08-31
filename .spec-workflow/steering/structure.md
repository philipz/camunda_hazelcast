# Project Structure

## Directory Organization

```
camunda_hazelcast/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/workflow/        # Root package following reverse domain
│   │   │       ├── Application.java         # Spring Boot main application class
│   │   │       ├── config/                  # Configuration classes
│   │   │       │   ├── HazelcastAutoConfiguration.java
│   │   │       │   ├── HazelcastProperties.java
│   │   │       │   ├── SessionConfig.java
│   │   │       │   └── HazelcastHealthIndicator.java
│   │   │       └── tasks/                   # Camunda service delegates
│   │   │           ├── getServiceDelegate.java
│   │   │           └── putServiceDelegate.java
│   │   └── resources/
│   │       ├── application.yaml             # Main configuration
│   │       ├── application-dev.yaml         # Development environment
│   │       ├── application-prod.yaml        # Production environment (if exists)
│   │       ├── process.bpmn                 # Main BPMN workflow
│   │       └── getprocess.bpmn              # Additional workflow
│   └── test/
│       ├── java/
│       │   └── com/example/workflow/
│       │       ├── config/                  # Configuration tests
│       │       │   ├── HazelcastPropertiesTest.java
│       │       │   └── SessionConfigurationTest.java
│       │       └── integration/             # Integration tests
│       │           ├── CamundaSessionE2ETest.java
│       │           ├── HazelcastWorkflowIntegrationTest.java
│       │           └── SessionIntegrationTest.java
│       └── resources/
│           └── application-test.yaml        # Test environment configuration
├── target/                                  # Maven build output (generated)
├── webproxy/                               # Docker web proxy configuration
│   ├── Dockerfile
│   └── proxy.conf
├── docker-compose.yml                      # Container orchestration
├── pom.xml                                 # Maven build configuration
├── README.md                               # Project documentation
├── CLAUDE.md                               # Development guidance
└── .spec-workflow/                         # Steering documents (generated)
    └── steering/
        ├── product.md
        ├── tech.md
        └── structure.md
```

## Naming Conventions

### Files
- **Configuration Classes**: `PascalCase` with descriptive suffixes (`HazelcastAutoConfiguration`, `SessionConfig`)
- **Service Delegates**: `camelCase` with `ServiceDelegate` suffix (`getServiceDelegate`, `putServiceDelegate`)
- **Test Classes**: Original class name + `Test` suffix (`HazelcastPropertiesTest`)
- **BPMN Files**: `lowercase` with `.bpmn` extension (`process.bpmn`, `getprocess.bpmn`)
- **Configuration Files**: `kebab-case` with environment suffix (`application-dev.yaml`)

### Code
- **Classes/Types**: `PascalCase` following Java conventions (`HazelcastProperties`, `SessionConfig`)
- **Methods**: `camelCase` with descriptive verbs (`getInstanceName`, `setMaxInactiveInterval`)
- **Constants**: `UPPER_SNAKE_CASE` for static final fields (`MAX_INACTIVE_INTERVAL_MINUTES`)
- **Variables**: `camelCase` for local variables and fields (`instanceName`, `backupCount`)
- **Package Names**: `lowercase` with logical grouping (`config`, `tasks`)

## Import Patterns

### Import Order
1. **Standard Java libraries**: `java.*` and `javax.*` imports
2. **Third-party dependencies**: Spring, Camunda, Hazelcast imports
3. **Project internal imports**: `com.example.workflow.*` imports
4. **Static imports**: Static method and constant imports (if used)

### Module/Package Organization
```java
// Example import structure in configuration classes:
import java.time.Duration;                          // Standard library
import org.springframework.boot.context.properties.ConfigurationProperties;  // Spring
import org.springframework.stereotype.Component;     // Spring
import com.hazelcast.config.Config;                 // Third-party
import com.example.workflow.config.HazelcastProperties;  // Internal
```

## Code Structure Patterns

### Configuration Class Organization
```java
@Component
@ConfigurationProperties(prefix = "hazelcast")
public class HazelcastProperties {
    // 1. Private fields
    private String instanceName = "default-value";
    
    // 2. Public getter/setter methods
    public String getInstanceName() { return instanceName; }
    public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
    
    // 3. Inner static classes for nested properties
    public static class Session { /* nested configuration */ }
}
```

### Service Delegate Organization
```java
@Component("getServiceDelegate")
public class getServiceDelegate implements JavaDelegate {
    // 1. Dependency injection
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    // 2. Main execution method
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // Input validation
        // Core logic
        // Result setting
    }
    
    // 3. Helper methods (if needed)
}
```

### Test Class Organization
```java
@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class SessionConfigurationTest {
    // 1. Test setup and configuration
    @Autowired
    private TestComponent testComponent;
    
    // 2. Test methods with descriptive names
    @Test
    @Order(1)
    void shouldConfigureSessionRepository() { /* test logic */ }
    
    // 3. Helper methods for test setup
    private void setupTestData() { /* setup logic */ }
}
```

## Code Organization Principles

1. **Single Responsibility**: Each class has one clear purpose (configuration, service task, health check)
2. **Modularity**: Logical separation by function (`config/` for configuration, `tasks/` for business logic)
3. **Testability**: Configuration and business logic designed for easy unit and integration testing
4. **Consistency**: Uniform patterns across all configuration and service classes

## Module Boundaries

### Configuration Module (`config/`)
- **Purpose**: All Spring configuration and property management
- **Dependencies**: Can depend on Spring Boot, Hazelcast, but not on business logic
- **Exports**: Configuration beans and properties for other modules

### Tasks Module (`tasks/`)
- **Purpose**: Camunda service delegates and workflow logic
- **Dependencies**: Can use configuration beans, Hazelcast services, Camunda APIs
- **Exports**: Service delegate beans for BPMN process execution

### Resource Boundaries
- **BPMN Files**: Process definitions isolated in `src/main/resources`
- **Configuration Files**: Environment-specific configurations with clear naming
- **Test Resources**: Separate test-specific configuration in `src/test/resources`

## Code Size Guidelines

### File Size Limits
- **Configuration Classes**: Maximum 200 lines (properties should be grouped logically)
- **Service Delegates**: Maximum 100 lines per delegate (complex logic should be extracted)
- **Test Classes**: Maximum 300 lines (multiple test methods acceptable)

### Method Complexity
- **Configuration Methods**: Simple getters/setters, minimal logic
- **Service Delegate Execute**: Maximum 50 lines, extract complex operations to helper methods
- **Test Methods**: Single assertion focus, maximum 20 lines per test method

### Class Responsibilities
- **Single Configuration Concern**: One `@ConfigurationProperties` class per configuration domain
- **One Service Task Per Class**: Each Camunda service delegate handles one workflow step
- **Test Class Per Production Class**: One test class per main class being tested

## Application Structure Patterns

### Spring Boot Application Organization
```java
@SpringBootApplication
public class Application {
    // Main method only - no business logic
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Configuration Properties Pattern
- **Hierarchical Structure**: Nested static classes for complex configuration
- **Default Values**: Sensible defaults provided for all properties
- **Validation**: Use Spring validation annotations where appropriate

### Service Delegate Pattern
- **Spring Bean Registration**: All delegates registered as Spring beans
- **BPMN Integration**: Referenced in BPMN using `#{beanName}` expression
- **Error Handling**: Comprehensive exception handling with meaningful messages

## Documentation Standards

### Code Documentation
- **Public APIs**: All public methods have Javadoc with `@param` and `@return`
- **Configuration Properties**: Document purpose and acceptable values
- **Complex Logic**: Inline comments explaining business rules
- **BPMN Processes**: Documentation annotations in BPMN files

### Project Documentation
- **README.md**: High-level project overview and setup instructions
- **CLAUDE.md**: Comprehensive development guidance and architectural decisions
- **Configuration Examples**: Sample configuration files for different environments