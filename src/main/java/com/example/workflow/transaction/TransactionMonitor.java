package com.example.workflow.transaction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive monitoring and logging component for distributed transactions.
 * 
 * This component provides:
 * - Transaction metrics collection and reporting
 * - Detailed audit trail for debugging and compliance
 * - Health monitoring and alerting
 * - Performance tracking and analysis
 * - Integration with Spring Boot Actuator
 */
@Component
public class TransactionMonitor implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(TransactionMonitor.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("transaction.audit");
    
    private final MeterRegistry meterRegistry;
    
    // Metrics counters
    private final Counter transactionsStarted;
    private final Counter transactionsCommitted;
    private final Counter transactionsRolledBack;
    private final Counter transactionsFailed;
    private final Counter deadlocksDetected;
    private final Counter timeoutsOccurred;
    
    // Timing metrics
    private final Timer transactionDuration;
    private final Timer commitDuration;
    private final Timer rollbackDuration;
    
    // Gauges for current state
    private final AtomicLong activeTransactionsCount = new AtomicLong(0);
    private final AtomicLong totalTransactionsProcessed = new AtomicLong(0);
    
    // Transaction tracking
    private final Map<String, TransactionAuditEntry> activeTransactions = new ConcurrentHashMap<>();
    private final Map<String, TransactionMetrics> transactionMetrics = new ConcurrentHashMap<>();
    
    // Performance tracking
    private volatile Duration lastTransactionDuration = Duration.ZERO;
    private volatile double successRate = 100.0;
    private volatile long errorCount = 0;
    
    public TransactionMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.transactionsStarted = Counter.builder("hazelcast.transactions.started")
            .description("Total number of transactions started")
            .register(meterRegistry);
            
        this.transactionsCommitted = Counter.builder("hazelcast.transactions.committed")
            .description("Total number of transactions committed successfully")
            .register(meterRegistry);
            
        this.transactionsRolledBack = Counter.builder("hazelcast.transactions.rolled_back")
            .description("Total number of transactions rolled back")
            .register(meterRegistry);
            
        this.transactionsFailed = Counter.builder("hazelcast.transactions.failed")
            .description("Total number of transactions that failed")
            .register(meterRegistry);
            
        this.deadlocksDetected = Counter.builder("hazelcast.transactions.deadlocks")
            .description("Total number of deadlocks detected")
            .register(meterRegistry);
            
        this.timeoutsOccurred = Counter.builder("hazelcast.transactions.timeouts")
            .description("Total number of transaction timeouts")
            .register(meterRegistry);
        
        // Initialize timers
        this.transactionDuration = Timer.builder("hazelcast.transactions.duration")
            .description("Transaction execution time")
            .register(meterRegistry);
            
        this.commitDuration = Timer.builder("hazelcast.transactions.commit.duration")
            .description("Transaction commit time")
            .register(meterRegistry);
            
        this.rollbackDuration = Timer.builder("hazelcast.transactions.rollback.duration")
            .description("Transaction rollback time")
            .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("hazelcast.transactions.active", this, monitor -> monitor.activeTransactionsCount.get())
            .description("Number of currently active transactions")
            .register(meterRegistry);
            
        Gauge.builder("hazelcast.transactions.success_rate", this, monitor -> monitor.successRate)
            .description("Transaction success rate percentage")
            .register(meterRegistry);
            
        Gauge.builder("hazelcast.transactions.last_duration_ms", this, monitor -> (double) monitor.lastTransactionDuration.toMillis())
            .description("Duration of the last completed transaction in milliseconds")
            .register(meterRegistry);
        
        logger.info("TransactionMonitor initialized with metrics collection enabled");
    }
    
    /**
     * Record the start of a new transaction.
     */
    public void recordTransactionStarted(String transactionId, String processInstanceId, 
                                       TransactionType type, List<String> participants) {
        transactionsStarted.increment();
        activeTransactionsCount.incrementAndGet();
        totalTransactionsProcessed.incrementAndGet();
        
        TransactionAuditEntry auditEntry = new TransactionAuditEntry(
            transactionId, processInstanceId, type, participants, Instant.now()
        );
        
        activeTransactions.put(transactionId, auditEntry);
        
        // Detailed audit logging
        auditLogger.info("TRANSACTION_STARTED: id={}, processInstanceId={}, type={}, participants={}, timestamp={}", 
                         transactionId, processInstanceId, type, participants, auditEntry.startTime);
        
        logger.debug("Transaction {} started for process {} with {} participants", 
                    transactionId, processInstanceId, participants.size());
    }
    
    /**
     * Record a successful transaction commit.
     */
    public void recordTransactionCommitted(String transactionId, Duration executionTime) {
        transactionsCommitted.increment();
        activeTransactionsCount.decrementAndGet();
        transactionDuration.record(executionTime);
        lastTransactionDuration = executionTime;
        
        TransactionAuditEntry auditEntry = activeTransactions.remove(transactionId);
        if (auditEntry != null) {
            auditEntry.setEndTime(Instant.now());
            auditEntry.setStatus(TransactionStatus.SUCCESS);
            auditEntry.setExecutionTime(executionTime);
            
            // Record commit timing separately
            Timer.Sample commitSample = Timer.start(meterRegistry);
            commitSample.stop(commitDuration);
            
            // Update success rate
            updateSuccessRate();
            
            // Detailed audit logging
            auditLogger.info("TRANSACTION_COMMITTED: id={}, processInstanceId={}, executionTime={}ms, totalOperations={}, timestamp={}", 
                           transactionId, auditEntry.processInstanceId, executionTime.toMillis(), 
                           auditEntry.participants.size(), auditEntry.endTime);
            
            logger.info("Transaction {} committed successfully in {}ms", transactionId, executionTime.toMillis());
        }
    }
    
    /**
     * Record a transaction rollback.
     */
    public void recordTransactionRolledBack(String transactionId, Duration executionTime, String reason) {
        transactionsRolledBack.increment();
        activeTransactionsCount.decrementAndGet();
        errorCount++;
        
        TransactionAuditEntry auditEntry = activeTransactions.remove(transactionId);
        if (auditEntry != null) {
            auditEntry.setEndTime(Instant.now());
            auditEntry.setStatus(TransactionStatus.ROLLBACK);
            auditEntry.setExecutionTime(executionTime);
            auditEntry.setErrorReason(reason);
            
            // Record rollback timing
            Timer.Sample rollbackSample = Timer.start(meterRegistry);
            rollbackSample.stop(rollbackDuration);
            
            // Update success rate
            updateSuccessRate();
            
            // Detailed audit logging
            auditLogger.warn("TRANSACTION_ROLLED_BACK: id={}, processInstanceId={}, executionTime={}ms, reason={}, timestamp={}", 
                            transactionId, auditEntry.processInstanceId, executionTime.toMillis(), 
                            reason, auditEntry.endTime);
            
            logger.warn("Transaction {} rolled back after {}ms: {}", transactionId, executionTime.toMillis(), reason);
        }
    }
    
    /**
     * Record a transaction failure.
     */
    public void recordTransactionFailed(String transactionId, Duration executionTime, Exception exception) {
        transactionsFailed.increment();
        activeTransactionsCount.decrementAndGet();
        errorCount++;
        
        TransactionAuditEntry auditEntry = activeTransactions.remove(transactionId);
        if (auditEntry != null) {
            auditEntry.setEndTime(Instant.now());
            auditEntry.setStatus(TransactionStatus.FAILED);
            auditEntry.setExecutionTime(executionTime);
            auditEntry.setErrorReason(exception.getMessage());
            
            // Update success rate
            updateSuccessRate();
            
            // Detailed audit logging
            auditLogger.error("TRANSACTION_FAILED: id={}, processInstanceId={}, executionTime={}ms, error={}, timestamp={}", 
                             transactionId, auditEntry.processInstanceId, executionTime.toMillis(), 
                             exception.getMessage(), auditEntry.endTime);
            
            logger.error("Transaction {} failed after {}ms", transactionId, executionTime.toMillis(), exception);
        }
    }
    
    /**
     * Record a deadlock detection event.
     */
    public void recordDeadlockDetected(String transactionId, String details) {
        deadlocksDetected.increment();
        
        auditLogger.warn("DEADLOCK_DETECTED: transactionId={}, details={}, timestamp={}", 
                        transactionId, details, Instant.now());
        
        logger.warn("Deadlock detected for transaction {}: {}", transactionId, details);
    }
    
    /**
     * Record a timeout event.
     */
    public void recordTimeoutOccurred(String transactionId, Duration configuredTimeout, Duration actualDuration) {
        timeoutsOccurred.increment();
        
        auditLogger.warn("TRANSACTION_TIMEOUT: id={}, configuredTimeout={}ms, actualDuration={}ms, timestamp={}", 
                        transactionId, configuredTimeout.toMillis(), actualDuration.toMillis(), Instant.now());
        
        logger.warn("Transaction {} timed out after {}ms (configured timeout: {}ms)", 
                   transactionId, actualDuration.toMillis(), configuredTimeout.toMillis());
    }
    
    /**
     * Record performance metrics for a transaction.
     */
    public void recordTransactionMetrics(String transactionId, TransactionMetrics metrics) {
        transactionMetrics.put(transactionId, metrics);
        
        logger.debug("Recorded performance metrics for transaction {}: operations={}, memory={}KB", 
                    transactionId, metrics.operationCount, metrics.memoryUsageKB);
    }
    
    /**
     * Get current transaction statistics.
     */
    public TransactionStatistics getTransactionStatistics() {
        long started = (long) transactionsStarted.count();
        long committed = (long) transactionsCommitted.count();
        long rolledBack = (long) transactionsRolledBack.count();
        long failed = (long) transactionsFailed.count();
        long active = activeTransactionsCount.get();
        
        return new TransactionStatistics(
            started, committed, rolledBack, failed, active,
            successRate, lastTransactionDuration, 
            (long) deadlocksDetected.count(), (long) timeoutsOccurred.count()
        );
    }
    
    /**
     * Get audit trail for a specific transaction.
     */
    public Optional<TransactionAuditEntry> getTransactionAudit(String transactionId) {
        return Optional.ofNullable(activeTransactions.get(transactionId));
    }
    
    /**
     * Get list of currently active transactions.
     */
    public List<TransactionAuditEntry> getActiveTransactions() {
        return new ArrayList<>(activeTransactions.values());
    }
    
    /**
     * Health check implementation for Spring Boot Actuator.
     */
    @Override
    public Health health() {
        Health.Builder health = Health.up();
        
        // Check if transaction system is healthy
        long activeCount = activeTransactionsCount.get();
        
        if (activeCount > 100) {
            health.down().withDetail("reason", "Too many active transactions: " + activeCount);
        }
        
        if (successRate < 95.0) {
            health.down().withDetail("reason", "Low success rate: " + String.format("%.2f%%", successRate));
        }
        
        // Add health details
        health.withDetail("activeTransactions", activeCount)
              .withDetail("successRate", String.format("%.2f%%", successRate))
              .withDetail("totalProcessed", totalTransactionsProcessed.get())
              .withDetail("lastTransactionDuration", lastTransactionDuration.toMillis() + "ms")
              .withDetail("deadlocksDetected", (long) deadlocksDetected.count())
              .withDetail("timeoutsOccurred", (long) timeoutsOccurred.count());
        
        return health.build();
    }
    
    /**
     * Clear all monitoring data (for testing purposes).
     */
    public void clearMetrics() {
        activeTransactions.clear();
        transactionMetrics.clear();
        activeTransactionsCount.set(0);
        totalTransactionsProcessed.set(0);
        errorCount = 0;
        successRate = 100.0;
        lastTransactionDuration = Duration.ZERO;
        
        logger.info("Transaction monitoring metrics cleared");
    }
    
    // Private helper methods
    
    private void updateSuccessRate() {
        long total = totalTransactionsProcessed.get();
        if (total > 0) {
            successRate = ((double) (total - errorCount) / total) * 100.0;
        }
    }
    
    // Data classes for monitoring
    
    public static class TransactionAuditEntry {
        private final String transactionId;
        private final String processInstanceId;
        private final TransactionType type;
        private final List<String> participants;
        private final Instant startTime;
        private Instant endTime;
        private TransactionStatus status = TransactionStatus.IN_PROGRESS;
        private Duration executionTime = Duration.ZERO;
        private String errorReason;
        
        public TransactionAuditEntry(String transactionId, String processInstanceId, 
                                   TransactionType type, List<String> participants, Instant startTime) {
            this.transactionId = transactionId;
            this.processInstanceId = processInstanceId;
            this.type = type;
            this.participants = new ArrayList<>(participants);
            this.startTime = startTime;
        }
        
        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public String getProcessInstanceId() { return processInstanceId; }
        public TransactionType getType() { return type; }
        public List<String> getParticipants() { return new ArrayList<>(participants); }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public TransactionStatus getStatus() { return status; }
        public Duration getExecutionTime() { return executionTime; }
        public String getErrorReason() { return errorReason; }
        
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public void setStatus(TransactionStatus status) { this.status = status; }
        public void setExecutionTime(Duration executionTime) { this.executionTime = executionTime; }
        public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
    }
    
    public static class TransactionMetrics {
        private final int operationCount;
        private final long memoryUsageKB;
        private final Duration networkLatency;
        private final int retryCount;
        
        public TransactionMetrics(int operationCount, long memoryUsageKB, 
                                Duration networkLatency, int retryCount) {
            this.operationCount = operationCount;
            this.memoryUsageKB = memoryUsageKB;
            this.networkLatency = networkLatency;
            this.retryCount = retryCount;
        }
        
        // Getters
        public int getOperationCount() { return operationCount; }
        public long getMemoryUsageKB() { return memoryUsageKB; }
        public Duration getNetworkLatency() { return networkLatency; }
        public int getRetryCount() { return retryCount; }
    }
    
    public static class TransactionStatistics {
        private final long transactionsStarted;
        private final long transactionsCommitted;
        private final long transactionsRolledBack;
        private final long transactionsFailed;
        private final long activeTransactions;
        private final double successRate;
        private final Duration lastTransactionDuration;
        private final long deadlocksDetected;
        private final long timeoutsOccurred;
        
        public TransactionStatistics(long started, long committed, long rolledBack, long failed,
                                   long active, double successRate, Duration lastDuration,
                                   long deadlocks, long timeouts) {
            this.transactionsStarted = started;
            this.transactionsCommitted = committed;
            this.transactionsRolledBack = rolledBack;
            this.transactionsFailed = failed;
            this.activeTransactions = active;
            this.successRate = successRate;
            this.lastTransactionDuration = lastDuration;
            this.deadlocksDetected = deadlocks;
            this.timeoutsOccurred = timeouts;
        }
        
        // Getters
        public long getTransactionsStarted() { return transactionsStarted; }
        public long getTransactionsCommitted() { return transactionsCommitted; }
        public long getTransactionsRolledBack() { return transactionsRolledBack; }
        public long getTransactionsFailed() { return transactionsFailed; }
        public long getActiveTransactions() { return activeTransactions; }
        public double getSuccessRate() { return successRate; }
        public Duration getLastTransactionDuration() { return lastTransactionDuration; }
        public long getDeadlocksDetected() { return deadlocksDetected; }
        public long getTimeoutsOccurred() { return timeoutsOccurred; }
    }
}
