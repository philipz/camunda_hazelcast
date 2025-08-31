package com.example.workflow.transaction;

/**
 * Enumeration of transaction isolation levels.
 */
public enum TransactionIsolation {
    /** Read uncommitted isolation level */
    READ_UNCOMMITTED,
    /** Read committed isolation level */
    READ_COMMITTED,
    /** Repeatable read isolation level */
    REPEATABLE_READ,
    /** Serializable isolation level */
    SERIALIZABLE
}
