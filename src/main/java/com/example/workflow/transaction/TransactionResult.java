package com.example.workflow.transaction;

import java.time.Duration;
import java.util.Map;

/**
 * Transaction result record containing execution results and metadata.
 * 
 * @param status Final status of the transaction (SUCCESS, FAILED, TIMEOUT, ROLLBACK)
 * @param executionTime Total execution time for the transaction
 * @param operations Map of operations performed during the transaction
 * @param failure Exception or error information if the transaction failed
 */
public record TransactionResult(
    TransactionStatus status,
    Duration executionTime,
    Map<String, Object> operations,
    String failure
) {}
