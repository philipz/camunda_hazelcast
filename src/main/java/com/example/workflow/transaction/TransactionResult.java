package com.example.workflow.transaction;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transaction result record containing operation outcome and metrics.
 * 
 * @param transactionId Associated transaction identifier
 * @param status Transaction completion status
 * @param executionTime Total transaction execution time
 * @param operations List of completed operations
 * @param failure Failure cause if transaction failed
 * @param metrics Performance and monitoring metrics
 */
public record TransactionResult(
    String transactionId,
    TransactionStatus status,
    Duration executionTime,
    List<String> operations,
    Optional<Exception> failure,
    Map<String, Object> metrics
) {
    /**
     * Get error message from failure if present.
     * @return Error message or empty string if no failure
     */
    public String errorMessage() {
        return failure.map(Throwable::getMessage).orElse("");
    }
}