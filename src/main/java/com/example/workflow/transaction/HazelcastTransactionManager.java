package com.example.workflow.transaction;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central coordinator for all distributed transaction operations using Hazelcast.
 * 
 * This class provides comprehensive transaction management including:
 * - Transaction lifecycle management (begin, commit, rollback)
 * - Active transaction registry and monitoring
 * - Timeout handling and cleanup
 * - Integration with Hazelcast's distributed transaction capabilities
 */
@Service
public class HazelcastTransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastTransactionManager.class);
    
    private final HazelcastInstance hazelcastInstance;
    private final IMap<String, com.example.workflow.transaction.TransactionContext> activeTransactions;
    private final Map<String, TransactionContext> hazelcastTransactionContexts;
    private final ScheduledExecutorService timeoutExecutor;
    
    @Autowired(required = false)
    private TransactionMonitor transactionMonitor;
    
    public HazelcastTransactionManager(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        this.activeTransactions = hazelcastInstance.getMap("active-transactions");
        this.hazelcastTransactionContexts = new ConcurrentHashMap<>();
        this.timeoutExecutor = Executors.newScheduledThreadPool(2);
        
        logger.info("HazelcastTransactionManager initialized with instance: {}", 
                   hazelcastInstance.getName());
    }
    
    /**
     * Begin a new distributed transaction.
     * 
     * @param processInstanceId The process instance ID associated with this transaction
     * @param participants List of transaction participants
     * @param options Transaction configuration options
     * @return TransactionContext containing transaction metadata
     * @throws TransactionException if transaction cannot be started
     */
    public com.example.workflow.transaction.TransactionContext beginTransaction(
            String processInstanceId, 
            List<String> participants, 
            com.example.workflow.transaction.TransactionOptions options) {
        
        String transactionId = UUID.randomUUID().toString();
        logger.info("Beginning transaction {} for process {}", transactionId, processInstanceId);
        
        try {
            // Create Hazelcast transaction options  
            TransactionOptions hazelcastOptions = new TransactionOptions()
                    .setTransactionType(mapTransactionType(options.type()))
                    .setTimeout(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
            
            // Begin Hazelcast transaction
            TransactionContext hazelcastContext = hazelcastInstance.newTransactionContext(hazelcastOptions);
            hazelcastContext.beginTransaction();
            
            // Create our transaction context
            com.example.workflow.transaction.TransactionContext transactionContext = 
                new com.example.workflow.transaction.TransactionContext(
                    transactionId,
                    processInstanceId,
                    options.type(),
                    participants
                );
            
            // Register in active transactions
            activeTransactions.put(transactionId, transactionContext);
            hazelcastTransactionContexts.put(transactionId, hazelcastContext);
            
            // Schedule timeout handling
            scheduleTimeout(transactionId, options.timeout());
            
            // Record monitoring event
            if (transactionMonitor != null) {
                transactionMonitor.recordTransactionStarted(transactionId, processInstanceId, 
                                                         options.type(), participants);
            }
            
            logger.info("Transaction {} successfully started", transactionId);
            return transactionContext;
            
        } catch (Exception e) {
            logger.error("Failed to begin transaction {} for process {}", 
                        transactionId, processInstanceId, e);
            
            // Record failure in monitoring
            if (transactionMonitor != null) {
                Duration executionTime = Duration.ofMillis(System.currentTimeMillis() - System.currentTimeMillis());
                transactionMonitor.recordTransactionFailed(transactionId, executionTime, e);
            }
            
            throw new TransactionException("Failed to begin transaction", e);
        }
    }
    
    /**
     * Commit an active transaction.
     * 
     * @param transactionId The ID of the transaction to commit
     * @return TransactionResult containing commit results
     * @throws TransactionException if commit fails
     */
    public com.example.workflow.transaction.TransactionResult commitTransaction(String transactionId) {
        logger.info("Committing transaction {}", transactionId);
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            com.example.workflow.transaction.TransactionContext context = activeTransactions.get(transactionId);
            if (context == null) {
                throw new TransactionException("Transaction not found: " + transactionId);
            }
            
            TransactionContext hazelcastContext = hazelcastTransactionContexts.get(transactionId);
            if (hazelcastContext == null) {
                throw new TransactionException("Hazelcast transaction context not found: " + transactionId);
            }
            
            // Commit the Hazelcast transaction
            hazelcastContext.commitTransaction();
            
            // Clean up
            cleanup(transactionId);
            
            Duration executionTime = Duration.between(startTime, LocalDateTime.now());
            
            // Record successful commit in monitoring
            if (transactionMonitor != null) {
                transactionMonitor.recordTransactionCommitted(transactionId, executionTime);
            }
            
            com.example.workflow.transaction.TransactionResult result = 
                new com.example.workflow.transaction.TransactionResult(
                    transactionId,
                    TransactionStatus.SUCCESS,
                    executionTime,
                    context.participants(),
                    Optional.empty(),
                    Map.of()
                );
            
            logger.info("Transaction {} committed successfully in {}ms", 
                       transactionId, executionTime.toMillis());
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to commit transaction {}", transactionId, e);
            
            Duration executionTime = Duration.between(startTime, LocalDateTime.now());
            
            // Record failure in monitoring
            if (transactionMonitor != null) {
                transactionMonitor.recordTransactionFailed(transactionId, executionTime, e);
            }
            
            // Attempt rollback on commit failure
            try {
                rollbackTransaction(transactionId);
            } catch (Exception rollbackException) {
                logger.error("Failed to rollback after commit failure for transaction {}", 
                           transactionId, rollbackException);
            }
            
            return new com.example.workflow.transaction.TransactionResult(
                transactionId,
                TransactionStatus.FAILED,
                executionTime,
                List.of(),
                Optional.of(e),
                Map.of()
            );
        }
    }
    
    /**
     * Rollback an active transaction.
     * 
     * @param transactionId The ID of the transaction to rollback
     * @return TransactionResult containing rollback results
     * @throws TransactionException if rollback fails
     */
    public com.example.workflow.transaction.TransactionResult rollbackTransaction(String transactionId) {
        logger.info("Rolling back transaction {}", transactionId);
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            com.example.workflow.transaction.TransactionContext context = activeTransactions.get(transactionId);
            TransactionContext hazelcastContext = hazelcastTransactionContexts.get(transactionId);
            
            if (hazelcastContext != null) {
                try {
                    hazelcastContext.rollbackTransaction();
                } catch (Exception e) {
                    logger.warn("Error during Hazelcast transaction rollback for {}: {}", 
                               transactionId, e.getMessage());
                }
            }
            
            // Clean up
            cleanup(transactionId);
            
            Duration executionTime = Duration.between(startTime, LocalDateTime.now());
            
            // Record rollback in monitoring
            if (transactionMonitor != null) {
                transactionMonitor.recordTransactionRolledBack(transactionId, executionTime, "Manual rollback");
            }
            
            com.example.workflow.transaction.TransactionResult result = 
                new com.example.workflow.transaction.TransactionResult(
                    transactionId,
                    TransactionStatus.ROLLBACK,
                    executionTime,
                    context != null ? context.participants() : List.of(),
                    Optional.empty(),
                    Map.of()
                );
            
            logger.info("Transaction {} rolled back successfully in {}ms", 
                       transactionId, executionTime.toMillis());
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to rollback transaction {}", transactionId, e);
            
            Duration executionTime = Duration.between(startTime, LocalDateTime.now());
            
            // Record failure in monitoring
            if (transactionMonitor != null) {
                transactionMonitor.recordTransactionFailed(transactionId, executionTime, e);
            }
            
            return new com.example.workflow.transaction.TransactionResult(
                transactionId,
                TransactionStatus.FAILED,
                executionTime,
                List.of(),
                Optional.of(e),
                Map.of()
            );
        }
    }
    
    /**
     * Get the status of a transaction.
     * 
     * @param transactionId The transaction ID
     * @return TransactionStatus or null if not found
     */
    public TransactionStatus getTransactionStatus(String transactionId) {
        com.example.workflow.transaction.TransactionContext context = activeTransactions.get(transactionId);
        if (context == null) {
            return null;
        }
        
        TransactionContext hazelcastContext = hazelcastTransactionContexts.get(transactionId);
        if (hazelcastContext == null) {
            return TransactionStatus.FAILED;
        }
        
        return TransactionStatus.IN_PROGRESS;
    }
    
    /**
     * Get all active transaction IDs.
     * 
     * @return Set of active transaction IDs
     */
    public java.util.Set<String> getActiveTransactionIds() {
        return activeTransactions.keySet();
    }
    
    /**
     * Get transaction context by ID.
     * 
     * @param transactionId The transaction ID
     * @return TransactionContext or null if not found
     */
    public com.example.workflow.transaction.TransactionContext getTransactionContext(String transactionId) {
        return activeTransactions.get(transactionId);
    }
    
    /**
     * Get the Hazelcast transaction context for service delegates.
     * 
     * @param transactionId The transaction ID
     * @return Hazelcast TransactionContext or null if not found
     */
    public TransactionContext getHazelcastTransactionContext(String transactionId) {
        return hazelcastTransactionContexts.get(transactionId);
    }
    
    /**
     * Schedule timeout handling for a transaction.
     */
    private void scheduleTimeout(String transactionId, Duration timeout) {
        timeoutExecutor.schedule(() -> {
            logger.warn("Transaction {} timed out after {}", transactionId, timeout);
            
            // Record timeout in monitoring
            if (transactionMonitor != null) {
                transactionMonitor.recordTimeoutOccurred(transactionId, timeout, timeout);
            }
            
            try {
                rollbackTransaction(transactionId);
            } catch (Exception e) {
                logger.error("Failed to rollback timed out transaction {}", transactionId, e);
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * Clean up transaction resources.
     */
    private void cleanup(String transactionId) {
        activeTransactions.remove(transactionId);
        hazelcastTransactionContexts.remove(transactionId);
        logger.debug("Cleaned up resources for transaction {}", transactionId);
    }
    
    /**
     * Map our transaction type to Hazelcast transaction type.
     */
    private com.hazelcast.transaction.TransactionOptions.TransactionType mapTransactionType(
            com.example.workflow.transaction.TransactionType type) {
        return switch (type) {
            case LOCAL -> com.hazelcast.transaction.TransactionOptions.TransactionType.TWO_PHASE;
            case XA, DISTRIBUTED -> com.hazelcast.transaction.TransactionOptions.TransactionType.TWO_PHASE;
            case SAGA -> com.hazelcast.transaction.TransactionOptions.TransactionType.TWO_PHASE;
            case TWO_PHASE -> com.hazelcast.transaction.TransactionOptions.TransactionType.TWO_PHASE;
            case ONE_PHASE -> com.hazelcast.transaction.TransactionOptions.TransactionType.ONE_PHASE;
        };
    }
    
    /**
     * Shutdown the transaction manager and clean up resources.
     */
    public void shutdown() {
        logger.info("Shutting down HazelcastTransactionManager");
        
        // Rollback any active transactions
        for (String transactionId : getActiveTransactionIds()) {
            try {
                rollbackTransaction(transactionId);
            } catch (Exception e) {
                logger.error("Error rolling back transaction {} during shutdown", transactionId, e);
            }
        }
        
        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("HazelcastTransactionManager shutdown complete");
    }
}
