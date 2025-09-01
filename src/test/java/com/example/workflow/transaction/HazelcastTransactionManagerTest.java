package com.example.workflow.transaction;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for HazelcastTransactionManager.
 * 
 * This test class validates the transaction lifecycle, error handling,
 * timeout scenarios, and resource management of the transaction manager.
 * Uses mocked Hazelcast dependencies for isolated testing.
 */
@ExtendWith(MockitoExtension.class)
public class HazelcastTransactionManagerTest {

    @Mock
    private HazelcastInstance hazelcastInstance;
    
    @Mock
    private IMap<Object, Object> activeTransactionsMap;
    
    @Mock
    private TransactionContext hazelcastTransactionContext;
    
    @Mock
    private TransactionOptions hazelcastTransactionOptions;
    
    private HazelcastTransactionManager transactionManager;
    
    private final String TEST_PROCESS_INSTANCE_ID = "process-123";
    private final List<String> TEST_PARTICIPANTS = List.of("RestServiceDelegate");
    
    @BeforeEach
    public void setUp() {
        // Setup mock behaviors BEFORE creating the transaction manager
        // Using lenient() to avoid PotentialStubbingProblem when stubs aren't used in all tests
        lenient().when(hazelcastInstance.getMap("active-transactions")).thenReturn(activeTransactionsMap);
        lenient().when(hazelcastInstance.newTransactionContext(any(TransactionOptions.class))).thenReturn(hazelcastTransactionContext);
        lenient().when(hazelcastInstance.getName()).thenReturn("test-hazelcast-instance");
        
        // Configure flexible transaction context stubbing for conditional operations
        lenient().doNothing().when(hazelcastTransactionContext).beginTransaction();
        lenient().doNothing().when(hazelcastTransactionContext).commitTransaction();
        lenient().doNothing().when(hazelcastTransactionContext).rollbackTransaction();
        
        transactionManager = new HazelcastTransactionManager(hazelcastInstance);
    }
    
    @Test
    public void testBeginTransaction_Success() {
        // Arrange
        com.example.workflow.transaction.TransactionOptions options = createDefaultTransactionOptions();
        
        // Act
        com.example.workflow.transaction.TransactionContext result = 
            transactionManager.beginTransaction(TEST_PROCESS_INSTANCE_ID, TEST_PARTICIPANTS, options);
        
        // Assert
        assertNotNull(result, "Transaction context should not be null");
        assertNotNull(result.transactionId(), "Transaction ID should be generated");
        assertEquals(TEST_PROCESS_INSTANCE_ID, result.processInstanceId(), 
                    "Process instance ID should match");
        assertEquals(TransactionType.DISTRIBUTED, result.type(), 
                    "Transaction type should match");
        assertEquals(TEST_PARTICIPANTS, result.participants(), 
                    "Participants should match");
        
        // Verify Hazelcast interactions
        verify(hazelcastInstance).newTransactionContext(any(TransactionOptions.class));
        verify(hazelcastTransactionContext).beginTransaction();
        verify(activeTransactionsMap).put(eq(result.transactionId()), eq(result));
    }
    
    @Test
    public void testBeginTransaction_WithDifferentTransactionTypes() {
        // Test TWO_PHASE transaction
        com.example.workflow.transaction.TransactionOptions twoPhaseOptions = 
            new com.example.workflow.transaction.TransactionOptions(
                TransactionType.DISTRIBUTED, Duration.ofSeconds(30), 
                TransactionIsolation.READ_COMMITTED, 3, false);
        
        com.example.workflow.transaction.TransactionContext twoPhaseResult = 
            transactionManager.beginTransaction(TEST_PROCESS_INSTANCE_ID, TEST_PARTICIPANTS, twoPhaseOptions);
        
        assertEquals(TransactionType.DISTRIBUTED, twoPhaseResult.type());
        
        // Test LOCAL transaction
        com.example.workflow.transaction.TransactionOptions localOptions = 
            new com.example.workflow.transaction.TransactionOptions(
                TransactionType.LOCAL, Duration.ofSeconds(15), 
                TransactionIsolation.READ_COMMITTED, 2, false);
        
        com.example.workflow.transaction.TransactionContext localResult = 
            transactionManager.beginTransaction(TEST_PROCESS_INSTANCE_ID, TEST_PARTICIPANTS, localOptions);
        
        assertEquals(TransactionType.LOCAL, localResult.type());
    }
    
    @Test
    public void testBeginTransaction_Failure() {
        // Arrange
        com.example.workflow.transaction.TransactionOptions options = createDefaultTransactionOptions();
        doThrow(new RuntimeException("Hazelcast error")).when(hazelcastTransactionContext).beginTransaction();
        
        // Act & Assert
        TransactionException exception = assertThrows(TransactionException.class, () -> {
            transactionManager.beginTransaction(TEST_PROCESS_INSTANCE_ID, TEST_PARTICIPANTS, options);
        });
        
        assertTrue(exception.getMessage().contains("Failed to begin transaction"));
        assertTrue(exception.getCause() instanceof RuntimeException);
    }
    
    @Test
    public void testCommitTransaction_Success() {
        // Arrange
        String transactionId = beginTestTransaction();
        com.example.workflow.transaction.TransactionContext mockContext = 
            createMockTransactionContext(transactionId);
        
        when(activeTransactionsMap.get(transactionId)).thenReturn(mockContext);
        
        // Act
        com.example.workflow.transaction.TransactionResult result = 
            transactionManager.commitTransaction(transactionId);
        
        // Assert
        assertNotNull(result, "Transaction result should not be null");
        assertEquals(TransactionStatus.SUCCESS, result.status(), "Transaction should be successful");
        assertNotNull(result.executionTime(), "Execution time should be recorded");
        assertEquals("", result.errorMessage(), "Error message should be empty for successful transaction");
        
        // Verify Hazelcast interactions
        verify(hazelcastTransactionContext).commitTransaction();
        verify(activeTransactionsMap).remove(transactionId);
    }
    
    @Test
    public void testCommitTransaction_NotFound() {
        // Arrange
        String nonExistentTransactionId = "non-existent-123";
        // Don't need to mock get() to return null - it returns null by default for unmocked calls
        
        // Act
        com.example.workflow.transaction.TransactionResult result = 
            transactionManager.commitTransaction(nonExistentTransactionId);
        
        // Assert
        assertEquals(TransactionStatus.FAILED, result.status(), "Transaction should be marked as failed");
        assertNotNull(result.errorMessage(), "Error message should be present for failed transaction");
        assertTrue(result.errorMessage().contains("Transaction not found"), 
                  "Error message should indicate transaction not found");
    }
    
    @Test
    public void testCommitTransaction_HazelcastFailure() {
        // Arrange
        String transactionId = beginTestTransaction();
        com.example.workflow.transaction.TransactionContext mockContext = 
            createMockTransactionContext(transactionId);
        
        when(activeTransactionsMap.get(transactionId)).thenReturn(mockContext);
        doThrow(new RuntimeException("Commit failed")).when(hazelcastTransactionContext).commitTransaction();
        
        // Act
        com.example.workflow.transaction.TransactionResult result = 
            transactionManager.commitTransaction(transactionId);
        
        // Assert
        assertEquals(TransactionStatus.FAILED, result.status(), "Transaction should be marked as failed");
        assertNotNull(result.errorMessage(), "Error message should be present");
        
        // Verify rollback was attempted
        verify(hazelcastTransactionContext).rollbackTransaction();
    }
    
    @Test
    public void testRollbackTransaction_Success() {
        // Arrange
        String transactionId = beginTestTransaction();
        com.example.workflow.transaction.TransactionContext mockContext = 
            createMockTransactionContext(transactionId);
        
        when(activeTransactionsMap.get(transactionId)).thenReturn(mockContext);
        
        // Act
        com.example.workflow.transaction.TransactionResult result = 
            transactionManager.rollbackTransaction(transactionId);
        
        // Assert
        assertNotNull(result, "Transaction result should not be null");
        assertEquals(TransactionStatus.ROLLBACK, result.status(), "Transaction should be rolled back");
        assertNotNull(result.executionTime(), "Execution time should be recorded");
        
        // Verify Hazelcast rollback interactions
        verify(hazelcastTransactionContext).rollbackTransaction();
        
        // Verify complete cleanup across all transaction participants
        verify(activeTransactionsMap).remove(transactionId);
        
        // Verify internal Hazelcast transaction context cleanup using reflection
        @SuppressWarnings("unchecked")
        java.util.Map<String, TransactionContext> hazelcastContextsMap = 
            (java.util.Map<String, TransactionContext>) ReflectionTestUtils.getField(transactionManager, "hazelcastTransactionContexts");
        assertNotNull(hazelcastContextsMap, "Hazelcast contexts map should not be null");
        assertFalse(hazelcastContextsMap.containsKey(transactionId), 
            "Hazelcast transaction context should be cleaned up");
    }
    
    @Test
    public void testRollbackTransaction_HazelcastFailure() {
        // Arrange
        String transactionId = beginTestTransaction();
        when(activeTransactionsMap.get(transactionId)).thenReturn(createMockTransactionContext(transactionId));
        doThrow(new RuntimeException("Rollback failed")).when(hazelcastTransactionContext).rollbackTransaction();
        
        // Act
        com.example.workflow.transaction.TransactionResult result = 
            transactionManager.rollbackTransaction(transactionId);
        
        // Assert
        assertEquals(TransactionStatus.ROLLBACK, result.status(), "Should still report rollback status");
        assertNotNull(result.executionTime(), "Execution time should be recorded");
        
        // Verify complete cleanup still happens even when Hazelcast rollback fails
        verify(activeTransactionsMap).remove(transactionId);
        
        // Verify internal Hazelcast transaction context cleanup using reflection
        @SuppressWarnings("unchecked")
        java.util.Map<String, TransactionContext> hazelcastContextsMap = 
            (java.util.Map<String, TransactionContext>) ReflectionTestUtils.getField(transactionManager, "hazelcastTransactionContexts");
        assertNotNull(hazelcastContextsMap, "Hazelcast contexts map should not be null");
        assertFalse(hazelcastContextsMap.containsKey(transactionId), 
            "Hazelcast transaction context should be cleaned up even on rollback failure");
    }
    
    @Test
    public void testGetTransactionStatus_ActiveTransaction() {
        // Arrange
        String transactionId = beginTestTransaction();
        com.example.workflow.transaction.TransactionContext mockContext = 
            createMockTransactionContext(transactionId);
        
        when(activeTransactionsMap.get(transactionId)).thenReturn(mockContext);
        
        // Act
        TransactionStatus status = transactionManager.getTransactionStatus(transactionId);
        
        // Assert
        assertEquals(TransactionStatus.IN_PROGRESS, status, "Active transaction should be in progress");
    }
    
    @Test
    public void testGetTransactionStatus_NotFound() {
        // Arrange
        String nonExistentTransactionId = "non-existent-123";
        when(activeTransactionsMap.get(nonExistentTransactionId)).thenReturn(null);
        
        // Act
        TransactionStatus status = transactionManager.getTransactionStatus(nonExistentTransactionId);
        
        // Assert
        assertNull(status, "Non-existent transaction should return null status");
    }
    
    @Test
    public void testGetActiveTransactionIds() {
        // Arrange
        Set<Object> expectedTransactionIds = Set.of("tx-1", "tx-2", "tx-3");
        when(activeTransactionsMap.keySet()).thenReturn(expectedTransactionIds);
        
        // Act
        Set<String> actualTransactionIds = transactionManager.getActiveTransactionIds();
        
        // Assert
        assertEquals(expectedTransactionIds, actualTransactionIds, 
                    "Active transaction IDs should match");
    }
    
    @Test
    public void testGetTransactionContext() {
        // Arrange
        String transactionId = "test-tx-123";
        com.example.workflow.transaction.TransactionContext expectedContext = 
            createMockTransactionContext(transactionId);
        
        when(activeTransactionsMap.get(transactionId)).thenReturn(expectedContext);
        
        // Act
        com.example.workflow.transaction.TransactionContext actualContext = 
            transactionManager.getTransactionContext(transactionId);
        
        // Assert
        assertEquals(expectedContext, actualContext, "Transaction context should match");
    }
    
    @Test
    public void testTransactionTimeout_Scenario() throws InterruptedException {
        // This test verifies timeout handling mechanism
        // Note: This is a simplified test as actual timeout testing would require
        // real-time delays which are not suitable for unit tests
        
        // Arrange
        com.example.workflow.transaction.TransactionOptions shortTimeoutOptions = 
            new com.example.workflow.transaction.TransactionOptions(
                TransactionType.DISTRIBUTED, Duration.ofMillis(100), // Very short timeout
                TransactionIsolation.READ_COMMITTED, 1, false);
        
        // Act
        com.example.workflow.transaction.TransactionContext context = 
            transactionManager.beginTransaction(TEST_PROCESS_INSTANCE_ID, TEST_PARTICIPANTS, shortTimeoutOptions);
        
        // Assert
        assertNotNull(context, "Transaction should be created even with short timeout");
        
        // Verify that timeout scheduling was set up (indirectly through successful creation)
        verify(hazelcastInstance).newTransactionContext(any(TransactionOptions.class));
    }
    
    @Test
    public void testShutdown_CleansUpActiveTransactions() {
        // Arrange - Create transaction contexts and mock the active transactions map
        String transactionId1 = "tx-shutdown-1";
        String transactionId2 = "tx-shutdown-2";
        
        com.example.workflow.transaction.TransactionContext mockContext1 = 
            createMockTransactionContext(transactionId1);
        com.example.workflow.transaction.TransactionContext mockContext2 = 
            createMockTransactionContext(transactionId2);
        
        // Mock the keySet to return our transaction IDs
        when(activeTransactionsMap.keySet()).thenReturn(Set.of(transactionId1, transactionId2));
        when(activeTransactionsMap.get(transactionId1)).thenReturn(mockContext1);
        when(activeTransactionsMap.get(transactionId2)).thenReturn(mockContext2);
        
        // Use reflection to populate the internal hazelcastTransactionContexts map
        // This is necessary because rollbackTransaction uses this private map
        @SuppressWarnings("unchecked")
        java.util.Map<String, TransactionContext> hazelcastContextsMap = 
            (java.util.Map<String, TransactionContext>) ReflectionTestUtils.getField(transactionManager, "hazelcastTransactionContexts");
        hazelcastContextsMap.put(transactionId1, hazelcastTransactionContext);
        hazelcastContextsMap.put(transactionId2, hazelcastTransactionContext);
        
        // Verify the setup - active transactions should be found
        assertEquals(2, transactionManager.getActiveTransactionIds().size(), 
                    "Should have 2 active transactions before shutdown");
        
        // Act
        transactionManager.shutdown();
        
        // Assert
        // Verify rollback was called for each active transaction
        verify(hazelcastTransactionContext, times(2)).rollbackTransaction();
        
        // Verify that both transactions were removed from the active map
        verify(activeTransactionsMap, times(2)).remove(anyString());
        
        // Verify complete cleanup of internal Hazelcast transaction contexts
        assertNotNull(hazelcastContextsMap, "Hazelcast contexts map should not be null");
        assertFalse(hazelcastContextsMap.containsKey(transactionId1), 
            "First Hazelcast transaction context should be cleaned up during shutdown");
        assertFalse(hazelcastContextsMap.containsKey(transactionId2), 
            "Second Hazelcast transaction context should be cleaned up during shutdown");
    }
    
    @Test
    public void testTransactionTypeMapping() {
        // This test verifies the internal mapping between our transaction types
        // and Hazelcast transaction types
        
        com.example.workflow.transaction.TransactionOptions distributedOptions = 
            new com.example.workflow.transaction.TransactionOptions(
                TransactionType.DISTRIBUTED, Duration.ofSeconds(30), 
                TransactionIsolation.READ_COMMITTED, 3, false);
        
        // Begin transaction to trigger type mapping
        transactionManager.beginTransaction(TEST_PROCESS_INSTANCE_ID, TEST_PARTICIPANTS, distributedOptions);
        
        // Verify that Hazelcast transaction options were created with correct type
        verify(hazelcastInstance).newTransactionContext(argThat(options -> 
            options.getTransactionType() == com.hazelcast.transaction.TransactionOptions.TransactionType.TWO_PHASE
        ));
    }
    
    @Test
    public void testConcurrentTransactions() {
        // Test multiple transactions can be managed concurrently
        
        com.example.workflow.transaction.TransactionOptions options = createDefaultTransactionOptions();
        
        // Begin multiple transactions
        com.example.workflow.transaction.TransactionContext tx1 = 
            transactionManager.beginTransaction("process-1", List.of("delegate-1"), options);
        com.example.workflow.transaction.TransactionContext tx2 = 
            transactionManager.beginTransaction("process-2", List.of("delegate-2"), options);
        
        // Verify they have different transaction IDs
        assertNotEquals(tx1.transactionId(), tx2.transactionId(), 
                       "Concurrent transactions should have different IDs");
        
        // Verify both are stored
        verify(activeTransactionsMap, times(2)).put(anyString(), any());
    }
    
    // Helper methods
    
    private com.example.workflow.transaction.TransactionOptions createDefaultTransactionOptions() {
        return new com.example.workflow.transaction.TransactionOptions(
            TransactionType.DISTRIBUTED,
            Duration.ofSeconds(30),
            TransactionIsolation.READ_COMMITTED,
            3,
            false
        );
    }
    
    private String beginTestTransaction() {
        com.example.workflow.transaction.TransactionOptions options = createDefaultTransactionOptions();
        com.example.workflow.transaction.TransactionContext context = 
            transactionManager.beginTransaction(TEST_PROCESS_INSTANCE_ID, TEST_PARTICIPANTS, options);
        return context.transactionId();
    }
    
    private com.example.workflow.transaction.TransactionContext createMockTransactionContext(String transactionId) {
        return new com.example.workflow.transaction.TransactionContext(
            transactionId,
            TEST_PROCESS_INSTANCE_ID,
            TransactionType.DISTRIBUTED,
            TEST_PARTICIPANTS
        );
    }
}
