# Tasks Document

- [x] 1. Extend HazelcastProperties with transaction configuration
  - File: src/main/java/com/example/workflow/config/HazelcastProperties.java
  - Add nested Transaction class with timeout, type, isolation settings
  - Define TransactionType enum (TWO_PHASE, ONE_PHASE)
  - Add configuration properties for retryCount, enableXA
  - Purpose: Provide externalized configuration for transaction behavior
  - _Leverage: existing HazelcastProperties pattern with Map and Session nested classes_
  - _Requirements: 4.1, 4.2_

- [x] 2. Create transaction data models as Java records
  - File: src/main/java/com/example/workflow/transaction/TransactionContext.java
  - Define TransactionContext record with transactionId, processInstanceId, type, participants
  - Add TransactionResult record with status, executionTime, operations, failure
  - Add TransactionOptions record with type, timeout, isolation, retryCount, enableXA
  - Purpose: Establish type-safe data structures for transaction management
  - _Leverage: Java 21 record features for immutable data classes_
  - _Requirements: 1.3, 4.4_

- [x] 3. Create HazelcastTransactionManager core class
  - File: src/main/java/com/example/workflow/transaction/HazelcastTransactionManager.java
  - Implement beginTransaction, commitTransaction, rollbackTransaction methods
  - Add transaction status tracking and timeout handling
  - Include transaction registry for active transaction management
  - Purpose: Central coordination for all distributed transaction operations
  - _Leverage: Hazelcast TransactionContext and TransactionOptions APIs_
  - _Requirements: 1.1, 1.4, 5.1_

- [ ] 4. Add transaction manager configuration to HazelcastAutoConfiguration
  - File: src/main/java/com/example/workflow/config/HazelcastAutoConfiguration.java (modify existing)
  - Add @Bean method for HazelcastTransactionManager
  - Configure transaction-specific Hazelcast maps with appropriate settings
  - Wire transaction manager with HazelcastInstance and properties
  - Purpose: Enable Spring-managed transaction infrastructure
  - _Leverage: existing HazelcastAutoConfiguration pattern and dependency injection_
  - _Requirements: 4.1, 4.2_

- [ ] 5. Create TransactionalServiceDelegate abstract base class
  - File: src/main/java/com/example/workflow/transaction/TransactionalServiceDelegate.java
  - Implement executeInTransaction method with callback pattern
  - Add getTransactionalMap and getTransactionalQueue helper methods
  - Include standardized error handling with transaction rollback
  - Purpose: Provide transactional execution framework for service delegates
  - _Leverage: Camunda JavaDelegate interface and existing service delegate patterns_
  - _Requirements: 3.1, 3.2, 5.2_

- [ ] 6. Enhance RestServiceDelegate with transaction support
  - File: src/main/java/com/example/workflow/tasks/RestServiceDelegate.java (modify existing)
  - Extend TransactionalServiceDelegate instead of implementing JavaDelegate directly
  - Wrap HTTP API calls and Hazelcast operations in transaction context
  - Add transaction-aware error handling and rollback logic for API failures
  - Purpose: Add distributed transaction capabilities to REST API coordination
  - _Leverage: existing RestServiceDelegate HTTP logic and error handling_
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 7. Create transaction utilities and helpers
  - File: src/main/java/com/example/workflow/transaction/TransactionUtils.java
  - Add retry logic with exponential backoff for transient failures
  - Implement deadlock detection and resolution utilities
  - Add transaction context propagation helpers for DelegateExecution
  - Purpose: Support common transaction operations and error recovery
  - _Leverage: existing error handling patterns and Camunda execution context_
  - _Requirements: 5.2, 5.3, 5.4_

- [ ] 8. Add transaction configuration to application.yaml
  - File: src/main/resources/application.yaml (modify existing)
  - Add hazelcast.transaction configuration section with defaults
  - Configure transaction timeout, type, and retry settings
  - Add environment-specific transaction configurations
  - Purpose: Enable external configuration of transaction behavior
  - _Leverage: existing hazelcast configuration structure_
  - _Requirements: 4.2, 4.3_

- [ ] 9. Create comprehensive transaction manager unit tests
  - File: src/test/java/com/example/workflow/transaction/HazelcastTransactionManagerTest.java
  - Test transaction lifecycle: begin, commit, rollback scenarios
  - Mock Hazelcast dependencies for isolated testing
  - Validate timeout handling and error scenarios
  - Purpose: Ensure transaction manager reliability and error handling
  - _Leverage: existing test patterns and Spring Boot Test framework_
  - _Requirements: 1.1, 1.4, 5.1, 5.5_

- [ ] 10. Create RestServiceDelegate transaction integration tests
  - File: src/test/java/com/example/workflow/tasks/RestServiceDelegateTransactionTest.java
  - Test successful transaction coordination between API calls and Hazelcast
  - Test rollback scenarios for API failures and Hazelcast failures
  - Validate transaction context propagation through DelegateExecution
  - Purpose: Ensure RestServiceDelegate transaction integration works correctly
  - _Leverage: existing RestServiceDelegate test patterns and Camunda test framework_
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 11. Create transaction configuration tests
  - File: src/test/java/com/example/workflow/config/TransactionConfigurationTest.java
  - Test Spring bean configuration and dependency injection
  - Validate transaction properties loading from application.yaml
  - Test different transaction configuration scenarios
  - Purpose: Ensure transaction infrastructure is properly configured
  - _Leverage: existing configuration test patterns from HazelcastPropertiesTest_
  - _Requirements: 4.1, 4.2, 4.3_

- [ ] 12. Use existing parallelprocess.bpmn for transaction demonstration
  - File: src/main/resources/parallelprocess.bpmn (use existing)
  - Leverage existing RestServiceDelegate task with multi-instance loop characteristics
  - Validate transaction behavior across multiple parallel API calls in same process
  - Test transaction coordination for apiCalls collection processing
  - Purpose: Demonstrate distributed transaction capabilities using existing workflow
  - _Leverage: existing parallelprocess.bpmn with restServiceDelegate and apiCalls collection_
  - _Requirements: 1.1, 2.1, 6.1_

- [ ] 13. Create end-to-end transaction workflow tests
  - File: src/test/java/com/example/workflow/integration/TransactionWorkflowE2ETest.java
  - Test complete parallelprocess.bpmn execution with transactional RestServiceDelegate
  - Validate ACID properties across multiple parallel API calls
  - Test performance requirements and concurrent transaction handling
  - Purpose: Validate distributed transaction functionality in realistic scenarios
  - _Leverage: existing integration test patterns and parallelprocess.bpmn_
  - _Requirements: 6.1, 6.3, 6.4, 6.5_

- [ ] 14. Add transaction monitoring and logging
  - File: src/main/java/com/example/workflow/transaction/TransactionMonitor.java
  - Implement transaction metrics collection and reporting
  - Add detailed logging for transaction lifecycle events
  - Create transaction audit trail for debugging and compliance
  - Purpose: Provide operational visibility into transaction system
  - _Leverage: existing logging patterns and Spring Boot Actuator integration_
  - _Requirements: 4.3, 4.4, 5.1_

- [ ] 15. Update application documentation
  - File: CLAUDE.md (modify existing)
  - Add section on distributed transaction usage and configuration
  - Document RestServiceDelegate transaction integration patterns
  - Include troubleshooting guide for common transaction issues
  - Purpose: Enable developers to effectively use transaction capabilities
  - _Leverage: existing CLAUDE.md documentation structure and patterns_
  - _Requirements: All requirements for developer experience_