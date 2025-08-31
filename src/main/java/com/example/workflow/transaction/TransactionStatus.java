package com.example.workflow.transaction;

/**
 * Enumeration of transaction statuses.
 */
public enum TransactionStatus {
    /** Transaction completed successfully */
    SUCCESS,
    /** Transaction failed */
    FAILED,
    /** Transaction timed out */
    TIMEOUT,
    /** Transaction was rolled back */
    ROLLBACK,
    /** Transaction is in progress */
    IN_PROGRESS,
    /** Transaction is pending */
    PENDING
}
