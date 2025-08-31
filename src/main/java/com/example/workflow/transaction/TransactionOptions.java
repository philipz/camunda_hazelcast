package com.example.workflow.transaction;

import java.time.Duration;

/**
 * Transaction options record containing configuration parameters.
 * 
 * @param type Type of transaction to execute
 * @param timeout Maximum time to wait for transaction completion
 * @param isolation Transaction isolation level
 * @param retryCount Number of retry attempts for failed transactions
 * @param enableXA Whether to enable XA (two-phase commit) protocol
 */
public record TransactionOptions(
    TransactionType type,
    Duration timeout,
    TransactionIsolation isolation,
    int retryCount,
    boolean enableXA
) {}
