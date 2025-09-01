package com.example.workflow.transaction;

/**
 * Enumeration of transaction isolation levels.
 */
public enum IsolationLevel {
    /** Read uncommitted data from other transactions */
    READ_UNCOMMITTED,
    
    /** Prevent dirty reads but allow non-repeatable reads and phantom reads */
    READ_COMMITTED,
    
    /** Prevent dirty reads and non-repeatable reads but allow phantom reads */
    REPEATABLE_READ,
    
    /** Prevent all read phenomena (dirty reads, non-repeatable reads, phantom reads) */
    SERIALIZABLE
}