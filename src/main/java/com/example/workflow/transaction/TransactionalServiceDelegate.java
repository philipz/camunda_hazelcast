package com.example.workflow.transaction;

import com.hazelcast.transaction.TransactionalMap;
import com.hazelcast.transaction.TransactionalQueue;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

/**
 * Abstract base class for service delegates that require transactional execution context.
 * 
 * This class provides a framework for wrapping business logic within distributed Hazelcast 
 * transactions, ensuring ACID properties across multiple operations. Service delegates 
 * extending this class can safely access shared Hazelcast data structures with automatic
 * transaction management and error handling.
 * 
 * Key Features:
 * - Automatic transaction lifecycle management (begin, commit, rollback)
 * - Transactional access to Hazelcast maps and queues  
 * - Standardized error handling with transaction rollback
 * - Process instance context integration with Camunda workflows
 * - Configurable transaction options and timeout handling
 */
public abstract class TransactionalServiceDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(TransactionalServiceDelegate.class);
    
    @Autowired
    protected HazelcastTransactionManager transactionManager;
    
    /**
     * Functional interface for transactional business logic execution.
     * 
     * @param <T> The return type of the business logic
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        /**
         * Execute business logic within a transaction context.
         * 
         * @param execution The Camunda delegate execution context
         * @param transactionContext The current transaction context
         * @return The result of the business logic execution
         * @throws Exception if business logic fails
         */
        T execute(DelegateExecution execution, TransactionContext transactionContext) throws Exception;
    }
    
    /**
     * Standard Camunda JavaDelegate execution method.
     * Delegates to executeTransactionally for transaction management.
     */
    @Override
    public final void execute(DelegateExecution execution) throws Exception {
        executeTransactionally(execution);
    }
    
    /**
     * Execute business logic within a distributed transaction.
     * 
     * This method handles the complete transaction lifecycle:
     * 1. Begin transaction with default options
     * 2. Execute business logic via executeInTransaction callback
     * 3. Commit transaction on success
     * 4. Rollback transaction on failure
     * 5. Handle timeouts and other exceptions
     * 
     * @param execution The Camunda delegate execution context
     * @throws Exception if transaction or business logic fails
     */
    protected void executeTransactionally(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        
        logger.info("Starting transactional execution for activity {} in process {}", 
                   activityId, processInstanceId);
        
        // Create default transaction options
        TransactionOptions options = createTransactionOptions(execution);
        
        // Participants include the current activity
        List<String> participants = List.of(activityId);
        
        TransactionContext transactionContext = null;
        try {
            // Begin transaction
            transactionContext = transactionManager.beginTransaction(
                processInstanceId, participants, options);
            
            logger.debug("Transaction {} started for activity {}", 
                       transactionContext.transactionId(), activityId);
            
            // Store transaction ID in execution context for potential use by other tasks
            execution.setVariable("transactionId", transactionContext.transactionId());
            
            // Execute business logic within transaction
            executeInTransaction(execution, transactionContext);
            
            // Commit transaction
            TransactionResult result = transactionManager.commitTransaction(
                transactionContext.transactionId());
            
            logger.info("Transaction {} committed successfully for activity {} in {}ms", 
                       transactionContext.transactionId(), activityId, 
                       result.executionTime().toMillis());
            
            // Store transaction result for monitoring
            execution.setVariable("transactionResult", "SUCCESS");
            execution.setVariable("transactionDuration", result.executionTime().toMillis());
            
        } catch (org.camunda.bpm.engine.delegate.BpmnError e) {
            // BpmnError should be handled by Camunda workflow engine, not rolled back
            logger.warn("BPMN error in activity {} in process {}: {}", 
                       activityId, processInstanceId, e.getMessage());
            
            // Commit transaction since this is a business logic error, not a technical failure
            try {
                TransactionResult result = transactionManager.commitTransaction(
                    transactionContext.transactionId());
                
                logger.info("Transaction {} committed successfully after BPMN error for activity {} in {}ms", 
                           transactionContext.transactionId(), activityId, 
                           result.executionTime().toMillis());
                
                // Store transaction result for monitoring
                execution.setVariable("transactionResult", "SUCCESS");
                execution.setVariable("transactionDuration", result.executionTime().toMillis());
                
            } catch (Exception commitException) {
                logger.error("Failed to commit transaction {} after BPMN error: {}", 
                           transactionContext.transactionId(), commitException.getMessage(), commitException);
                handleTransactionFailure(commitException, execution, transactionContext);
            }
            
            // Re-throw BpmnError to Camunda for proper workflow handling
            throw e;
        } catch (Exception e) {
            logger.error("Transaction failed for activity {} in process {}: {}", 
                        activityId, processInstanceId, e.getMessage(), e);
            
            // Handle transaction failure
            handleTransactionFailure(e, execution, transactionContext);
            
            // Re-throw to propagate to Camunda
            throw e;
        }
    }
    
    /**
     * Execute business logic within an active transaction context.
     * 
     * Subclasses must implement this method to define their specific business logic.
     * The transaction context is already established and will be automatically
     * committed or rolled back based on the execution outcome.
     * 
     * @param execution The Camunda delegate execution context
     * @param transactionContext The active transaction context
     * @throws Exception if business logic fails (triggers rollback)
     */
    protected abstract void executeInTransaction(DelegateExecution execution, 
                                               TransactionContext transactionContext) throws Exception;
    
    /**
     * Execute business logic with a callback pattern within the current transaction.
     * 
     * This method provides an alternative approach for cases where lambda expressions
     * or method references are preferred over subclass implementation.
     * 
     * @param execution The Camunda delegate execution context
     * @param callback The business logic to execute
     * @param <T> The return type of the callback
     * @return The result of the callback execution
     * @throws Exception if callback execution fails
     */
    protected <T> T executeInTransaction(DelegateExecution execution, 
                                       TransactionCallback<T> callback) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        
        // Create transaction options
        TransactionOptions options = createTransactionOptions(execution);
        List<String> participants = List.of(activityId);
        
        TransactionContext transactionContext = null;
        try {
            // Begin transaction
            transactionContext = transactionManager.beginTransaction(
                processInstanceId, participants, options);
            
            logger.debug("Callback transaction {} started for activity {}", 
                       transactionContext.transactionId(), activityId);
            
            // Execute callback
            T result = callback.execute(execution, transactionContext);
            
            // Commit transaction
            transactionManager.commitTransaction(transactionContext.transactionId());
            
            logger.debug("Callback transaction {} committed successfully", 
                       transactionContext.transactionId());
            
            return result;
            
        } catch (Exception e) {
            handleTransactionFailure(e, execution, transactionContext);
            throw e;
        }
    }
    
    /**
     * Get a transactional map for ACID operations.
     * 
     * @param mapName The name of the Hazelcast map
     * @param transactionContext The active transaction context
     * @param <K> The key type
     * @param <V> The value type
     * @return TransactionalMap interface for atomic operations
     */
    protected <K, V> TransactionalMap<K, V> getTransactionalMap(String mapName, 
                                                               TransactionContext transactionContext) {
        logger.debug("Getting transactional map '{}' for transaction {}", 
                   mapName, transactionContext.transactionId());
        
        // Get the Hazelcast transaction context from the transaction manager
        com.hazelcast.transaction.TransactionContext hazelcastContext = 
            getHazelcastTransactionContext(transactionContext);
        
        return hazelcastContext.getMap(mapName);
    }
    
    /**
     * Get a transactional queue for reliable messaging operations.
     * 
     * @param queueName The name of the Hazelcast queue
     * @param transactionContext The active transaction context
     * @param <E> The element type
     * @return TransactionalQueue interface for atomic operations
     */
    protected <E> TransactionalQueue<E> getTransactionalQueue(String queueName, 
                                                             TransactionContext transactionContext) {
        logger.debug("Getting transactional queue '{}' for transaction {}", 
                   queueName, transactionContext.transactionId());
        
        // Get the Hazelcast transaction context from the transaction manager
        com.hazelcast.transaction.TransactionContext hazelcastContext = 
            getHazelcastTransactionContext(transactionContext);
        
        return hazelcastContext.getQueue(queueName);
    }
    
    /**
     * Handle transaction failure with standardized error handling and rollback.
     * 
     * @param exception The exception that caused the failure
     * @param execution The Camunda delegate execution context
     * @param transactionContext The failed transaction context (may be null)
     */
    protected void handleTransactionFailure(Exception exception, 
                                          DelegateExecution execution,
                                          TransactionContext transactionContext) {
        String activityId = execution.getCurrentActivityId();
        String processInstanceId = execution.getProcessInstanceId();
        
        logger.error("Handling transaction failure for activity {} in process {}", 
                   activityId, processInstanceId, exception);
        
        // Set failure information in execution context
        execution.setVariable("transactionResult", "FAILED");
        execution.setVariable("transactionError", exception.getMessage());
        
        // Attempt rollback if transaction context exists
        if (transactionContext != null) {
            try {
                TransactionResult rollbackResult = transactionManager.rollbackTransaction(
                    transactionContext.transactionId());
                
                logger.info("Transaction {} rolled back successfully in {}ms", 
                           transactionContext.transactionId(), 
                           rollbackResult.executionTime().toMillis());
                
                execution.setVariable("transactionRollback", "SUCCESS");
                
            } catch (Exception rollbackException) {
                logger.error("Failed to rollback transaction {} for activity {}: {}", 
                           transactionContext.transactionId(), activityId, 
                           rollbackException.getMessage(), rollbackException);
                
                execution.setVariable("transactionRollback", "FAILED");
                execution.setVariable("rollbackError", rollbackException.getMessage());
            }
        }
    }
    
    /**
     * Create transaction options based on execution context and configuration.
     * 
     * Subclasses can override this method to customize transaction behavior
     * based on specific business requirements or process variables.
     * 
     * @param execution The Camunda delegate execution context
     * @return Transaction options for the current execution
     */
    protected TransactionOptions createTransactionOptions(DelegateExecution execution) {
        // Default transaction options
        return new TransactionOptions(
            TransactionType.DISTRIBUTED,  // Use distributed transactions by default
            Duration.ofSeconds(30),       // 30 second timeout
            TransactionIsolation.READ_COMMITTED,  // Standard isolation level
            3,                           // 3 retry attempts
            false                        // XA disabled by default
        );
    }
    
    /**
     * Get the underlying Hazelcast transaction context.
     * 
     * This is a helper method for internal use to bridge between our
     * transaction context and Hazelcast's native transaction context.
     * 
     * @param transactionContext Our transaction context
     * @return Hazelcast transaction context
     */
    private com.hazelcast.transaction.TransactionContext getHazelcastTransactionContext(
            TransactionContext transactionContext) {
        String transactionId = transactionContext.transactionId();
        com.hazelcast.transaction.TransactionContext hazelcastContext = 
            transactionManager.getHazelcastTransactionContext(transactionId);
            
        if (hazelcastContext == null) {
            throw new IllegalStateException(
                "Hazelcast transaction context not found for transaction: " + transactionId);
        }
        
        return hazelcastContext;
    }
}
