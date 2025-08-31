package com.example.workflow.transaction;

/**
 * Exception thrown when transaction operations fail.
 * 
 * This runtime exception is used to signal various transaction-related errors
 * including start failures, commit failures, rollback failures, and timeout issues.
 */
public class TransactionException extends RuntimeException {
    
    /**
     * Constructs a new transaction exception with the specified detail message.
     * 
     * @param message the detail message
     */
    public TransactionException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new transaction exception with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public TransactionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new transaction exception with the specified cause.
     * 
     * @param cause the cause of the exception
     */
    public TransactionException(Throwable cause) {
        super(cause);
    }
}
