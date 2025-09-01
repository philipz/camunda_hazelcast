package com.example.workflow.transaction;

/**
 * Enumeration of supported transaction types.
 */
public enum TransactionType {
    /** Local transaction within a single resource */
    LOCAL,
    /** Distributed transaction across multiple resources */
    DISTRIBUTED,
    /** XA (two-phase commit) transaction */
    XA,
    /** Saga pattern transaction */
    SAGA,
    /** Two-phase commit transaction */
    TWO_PHASE,
    /** Single-phase commit transaction (for performance) */
    ONE_PHASE
}
