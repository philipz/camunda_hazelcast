package com.example.workflow.transaction;

import java.util.List;

/**
 * Transaction context record containing transaction metadata and participant information.
 * 
 * @param transactionId Unique identifier for the transaction
 * @param processInstanceId Process instance identifier associated with this transaction
 * @param type Type of the transaction (e.g., DISTRIBUTED, LOCAL, XA)
 * @param participants List of transaction participants (services, databases, etc.)
 */
public record TransactionContext(
    String transactionId,
    String processInstanceId,
    TransactionType type,
    List<String> participants
) {}
