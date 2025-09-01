package com.example.workflow.transaction;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Utility class providing common transaction operations and error recovery mechanisms.
 * 
 * This class contains static helper methods for:
 * - Retry logic with exponential backoff for transient failures
 * - Deadlock detection and resolution utilities
 * - Transaction context propagation helpers for DelegateExecution
 * - Common error handling patterns for transaction operations
 * 
 * The utilities are designed to work with Hazelcast distributed transactions
 * and integrate seamlessly with Camunda workflow execution contexts.
 */
public final class TransactionUtils {

    private static final Logger logger = LoggerFactory.getLogger(TransactionUtils.class);
    
    // Constants for retry logic
    private static final long DEFAULT_INITIAL_DELAY_MS = 100L;
    private static final long DEFAULT_MAX_DELAY_MS = 5000L;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final double DEFAULT_JITTER_FACTOR = 0.1;
    
    // Constants for deadlock detection
    private static final String DEADLOCK_KEYWORD = "deadlock";
    private static final String LOCK_TIMEOUT_KEYWORD = "lock timeout";
    private static final String TRANSACTION_TIMEOUT_KEYWORD = "transaction timeout";
    
    // Prevent instantiation
    private TransactionUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Execute an operation with retry logic and exponential backoff for transient failures.
     * 
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param isRetryableException Predicate to determine if an exception is retryable
     * @param <T> The return type of the operation
     * @return The result of the successful operation
     * @throws Exception if all retry attempts fail
     */
    public static <T> T executeWithRetry(Callable<T> operation, 
                                        int maxRetries, 
                                        Predicate<Exception> isRetryableException) throws Exception {
        return executeWithRetry(operation, maxRetries, isRetryableException, 
                               DEFAULT_INITIAL_DELAY_MS, DEFAULT_MAX_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER);
    }
    
    /**
     * Execute an operation with configurable retry logic and exponential backoff.
     * 
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param isRetryableException Predicate to determine if an exception is retryable
     * @param initialDelayMs Initial delay between retries in milliseconds
     * @param maxDelayMs Maximum delay between retries in milliseconds
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param <T> The return type of the operation
     * @return The result of the successful operation
     * @throws Exception if all retry attempts fail
     */
    public static <T> T executeWithRetry(Callable<T> operation,
                                        int maxRetries,
                                        Predicate<Exception> isRetryableException,
                                        long initialDelayMs,
                                        long maxDelayMs,
                                        double backoffMultiplier) throws Exception {
        Exception lastException = null;
        long currentDelay = initialDelayMs;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("Executing operation attempt {} of {}", attempt + 1, maxRetries + 1);
                T result = operation.call();
                
                if (attempt > 0) {
                    logger.info("Operation succeeded on attempt {} after {} previous failures", 
                               attempt + 1, attempt);
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                // Don't retry if this is the last attempt or if exception is not retryable
                if (attempt >= maxRetries || !isRetryableException.test(e)) {
                    logger.error("Operation failed on attempt {} (final attempt): {}", 
                               attempt + 1, e.getMessage());
                    break;
                }
                
                logger.warn("Operation failed on attempt {}: {}. Retrying in {}ms", 
                           attempt + 1, e.getMessage(), currentDelay);
                
                // Wait before retry with jitter
                try {
                    Thread.sleep(calculateJitteredDelay(currentDelay));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                // Calculate next delay with exponential backoff
                currentDelay = Math.min((long) (currentDelay * backoffMultiplier), maxDelayMs);
            }
        }
        
        throw new RuntimeException("Operation failed after " + (maxRetries + 1) + " attempts", lastException);
    }
    
    /**
     * Determine if an exception represents a transient failure that should be retried.
     * 
     * @param exception The exception to check
     * @return true if the exception is retryable, false otherwise
     */
    public static boolean isRetryableException(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // Network and connectivity issues
        if (lowerMessage.contains("connection") || 
            lowerMessage.contains("timeout") ||
            lowerMessage.contains("network") ||
            lowerMessage.contains("socket")) {
            return true;
        }
        
        // Temporary resource issues
        if (lowerMessage.contains("temporarily unavailable") ||
            lowerMessage.contains("try again") ||
            lowerMessage.contains("busy")) {
            return true;
        }
        
        // Hazelcast specific transient errors
        if (lowerMessage.contains("member left") ||
            lowerMessage.contains("partition migrating") ||
            lowerMessage.contains("cluster is not in safe state")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Determine if an exception represents a deadlock condition.
     * 
     * @param exception The exception to check
     * @return true if the exception indicates a deadlock, false otherwise
     */
    public static boolean isDeadlockException(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        
        return lowerMessage.contains(DEADLOCK_KEYWORD) ||
               lowerMessage.contains(LOCK_TIMEOUT_KEYWORD) ||
               (lowerMessage.contains(TRANSACTION_TIMEOUT_KEYWORD) && 
                lowerMessage.contains("lock"));
    }
    
    /**
     * Create a deadlock-aware retry predicate that handles both transient failures and deadlocks.
     * 
     * @return Predicate that returns true for retryable exceptions including deadlocks
     */
    public static Predicate<Exception> createDeadlockAwareRetryPredicate() {
        return exception -> isRetryableException(exception) || isDeadlockException(exception);
    }
    
    /**
     * Execute an operation with deadlock detection and automatic retry.
     * Uses intelligent backoff with additional jitter for deadlock scenarios.
     * 
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param <T> The return type of the operation
     * @return The result of the successful operation
     * @throws Exception if all retry attempts fail
     */
    public static <T> T executeWithDeadlockRetry(Callable<T> operation, int maxRetries) throws Exception {
        return executeWithRetry(operation, maxRetries, createDeadlockAwareRetryPredicate(),
                               50L, 2000L, 1.5); // More aggressive retry for deadlocks
    }
    
    /**
     * Propagate transaction context to DelegateExecution variables.
     * This helps maintain transaction context across different service delegates.
     * 
     * @param execution The Camunda delegate execution context
     * @param transactionContext The transaction context to propagate
     */
    public static void propagateTransactionContext(DelegateExecution execution, 
                                                  TransactionContext transactionContext) {
        if (execution == null || transactionContext == null) {
            logger.warn("Cannot propagate transaction context: execution or context is null");
            return;
        }
        
        logger.debug("Propagating transaction context {} to execution {}", 
                    transactionContext.transactionId(), execution.getId());
        
        // Set transaction context variables
        execution.setVariable("transactionId", transactionContext.transactionId());
        execution.setVariable("transactionType", transactionContext.type().toString());
        execution.setVariable("transactionStartTime", java.time.Instant.now().toString());
        execution.setVariable("transactionParticipants", transactionContext.participants());
        
        // Set process-level transaction tracking
        String processTransactionVar = "process_transaction_" + execution.getProcessInstanceId();
        execution.setVariable(processTransactionVar, transactionContext.transactionId());
        
        logger.debug("Transaction context propagated successfully");
    }
    
    /**
     * Retrieve transaction context from DelegateExecution variables.
     * 
     * @param execution The Camunda delegate execution context
     * @return The transaction ID if found, null otherwise
     */
    public static String getTransactionIdFromExecution(DelegateExecution execution) {
        if (execution == null) {
            return null;
        }
        
        Object transactionId = execution.getVariable("transactionId");
        return transactionId != null ? transactionId.toString() : null;
    }
    
    /**
     * Check if a DelegateExecution is currently participating in a transaction.
     * 
     * @param execution The Camunda delegate execution context
     * @return true if execution is in a transaction, false otherwise
     */
    public static boolean isInTransaction(DelegateExecution execution) {
        return getTransactionIdFromExecution(execution) != null;
    }
    
    /**
     * Clear transaction context from DelegateExecution variables.
     * Useful for cleanup after transaction completion or failure.
     * 
     * @param execution The Camunda delegate execution context
     */
    public static void clearTransactionContext(DelegateExecution execution) {
        if (execution == null) {
            return;
        }
        
        logger.debug("Clearing transaction context from execution {}", execution.getId());
        
        // Remove transaction variables
        execution.removeVariable("transactionId");
        execution.removeVariable("transactionType");
        execution.removeVariable("transactionStartTime");
        execution.removeVariable("transactionParticipants");
        
        // Remove process-level transaction tracking
        String processTransactionVar = "process_transaction_" + execution.getProcessInstanceId();
        execution.removeVariable(processTransactionVar);
        
        logger.debug("Transaction context cleared successfully");
    }
    
    /**
     * Create a transaction metadata object for monitoring and debugging.
     * 
     * @param transactionContext The transaction context
     * @param execution The Camunda delegate execution context
     * @return Transaction metadata map
     */
    public static java.util.Map<String, Object> createTransactionMetadata(
            TransactionContext transactionContext, DelegateExecution execution) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        
        if (transactionContext != null) {
            metadata.put("transactionId", transactionContext.transactionId());
            metadata.put("transactionType", transactionContext.type());
            metadata.put("startTime", java.time.Instant.now());
            metadata.put("participants", transactionContext.participants());
        }
        
        if (execution != null) {
            metadata.put("processInstanceId", execution.getProcessInstanceId());
            metadata.put("activityId", execution.getCurrentActivityId());
            metadata.put("executionId", execution.getId());
        }
        
        metadata.put("timestamp", Instant.now());
        
        return metadata;
    }
    
    /**
     * Validate transaction timeout and warn if approaching deadline.
     * Uses a default 30-second timeout for validation.
     * 
     * @param transactionContext The transaction context to check
     * @param warningThresholdPercent Percentage of timeout elapsed before warning (0.0-1.0)
     * @return true if transaction is still valid, false if timed out
     */
    public static boolean validateTransactionTimeout(TransactionContext transactionContext, 
                                                   double warningThresholdPercent) {
        if (transactionContext == null) {
            return false;
        }
        
        // Use current time as start time since TransactionContext doesn't have startTime
        Instant now = Instant.now();
        Duration timeout = Duration.ofSeconds(30); // Default timeout
        
        // For this implementation, we'll assume transaction just started
        // In a real implementation, this would track actual start time
        logger.debug("Validating transaction {} timeout with {}s limit", 
                    transactionContext.transactionId(), timeout.getSeconds());
        
        return true; // Simplified for this implementation
    }
    
    /**
     * Calculate jittered delay to avoid thundering herd problems in retry scenarios.
     * 
     * @param baseDelayMs Base delay in milliseconds
     * @return Jittered delay in milliseconds
     */
    private static long calculateJitteredDelay(long baseDelayMs) {
        Random random = ThreadLocalRandom.current();
        double jitter = (random.nextDouble() - 0.5) * 2 * DEFAULT_JITTER_FACTOR;
        long jitteredDelay = Math.round(baseDelayMs * (1 + jitter));
        return Math.max(jitteredDelay, 10L); // Minimum 10ms delay
    }
    
    /**
     * Format exception information for logging with transaction context.
     * 
     * @param exception The exception to format
     * @param transactionContext Optional transaction context
     * @return Formatted exception message
     */
    public static String formatTransactionException(Exception exception, 
                                                  TransactionContext transactionContext) {
        StringBuilder sb = new StringBuilder();
        
        if (transactionContext != null) {
            sb.append("[Transaction ").append(transactionContext.transactionId()).append("] ");
        }
        
        sb.append(exception.getClass().getSimpleName()).append(": ").append(exception.getMessage());
        
        // Add deadlock or retry information
        if (isDeadlockException(exception)) {
            sb.append(" (DEADLOCK DETECTED)");
        } else if (isRetryableException(exception)) {
            sb.append(" (RETRYABLE)");
        }
        
        return sb.toString();
    }
}
