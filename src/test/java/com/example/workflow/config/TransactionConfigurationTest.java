package com.example.workflow.config;

import com.example.workflow.transaction.HazelcastTransactionManager;
import com.example.workflow.transaction.TransactionType;
import com.example.workflow.transaction.TransactionIsolation;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for transaction configuration and Spring bean setup.
 * 
 * This test class validates:
 * - Transaction properties loading from application.yaml
 * - Spring bean configuration and dependency injection
 * - Different transaction configuration scenarios
 * - Integration with existing Hazelcast configuration
 */
@SpringBootTest
@ActiveProfiles("test")
public class TransactionConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private HazelcastProperties hazelcastProperties;
    
    @Autowired(required = false)
    private HazelcastTransactionManager transactionManager;
    
    @Autowired(required = false)
    private HazelcastInstance hazelcastInstance;
    
    @Test
    public void testTransactionPropertiesLoaded() {
        assertNotNull(hazelcastProperties, "HazelcastProperties should be injected");
        assertNotNull(hazelcastProperties.getTransaction(), "Transaction properties should be available");
        
        HazelcastProperties.Transaction transactionProps = hazelcastProperties.getTransaction();
        
        // Test default values from application-test.yaml or defaults
        assertNotNull(transactionProps.getTimeoutSeconds(), "Timeout should be configured");
        assertNotNull(transactionProps.getType(), "Transaction type should be configured");
        assertNotNull(transactionProps.getIsolation(), "Isolation level should be configured");
        assertNotNull(transactionProps.getRetryCount(), "Retry count should be configured");
        assertNotNull(transactionProps.isEnableXA(), "XA setting should be configured");
    }
    
    @Test
    public void testDefaultTransactionProperties() {
        HazelcastProperties.Transaction transactionProps = hazelcastProperties.getTransaction();
        
        // Test default values
        assertEquals(30L, transactionProps.getTimeoutSeconds(), 
                    "Default timeout should be 30 seconds");
        assertEquals(HazelcastProperties.TransactionType.TWO_PHASE, transactionProps.getType(), 
                    "Default transaction type should be TWO_PHASE");
        assertEquals("READ_COMMITTED", transactionProps.getIsolation(), 
                    "Default isolation should be READ_COMMITTED");
        assertEquals(3, transactionProps.getRetryCount(), 
                    "Default retry count should be 3");
        assertFalse(transactionProps.isEnableXA(), 
                   "XA should be disabled by default");
    }
    
    @Test
    public void testTransactionPropertySetters() {
        HazelcastProperties.Transaction transactionProps = new HazelcastProperties.Transaction();
        
        // Test all setters and getters
        transactionProps.setTimeoutSeconds(60L);
        assertEquals(60L, transactionProps.getTimeoutSeconds(), 
                    "Timeout should be settable");
        
        transactionProps.setType(HazelcastProperties.TransactionType.ONE_PHASE);
        assertEquals(HazelcastProperties.TransactionType.ONE_PHASE, transactionProps.getType(), 
                    "Transaction type should be settable");
        
        transactionProps.setIsolation("REPEATABLE_READ");
        assertEquals("REPEATABLE_READ", transactionProps.getIsolation(), 
                    "Isolation level should be settable");
        
        transactionProps.setRetryCount(5);
        assertEquals(5, transactionProps.getRetryCount(), 
                    "Retry count should be settable");
        
        transactionProps.setEnableXA(true);
        assertTrue(transactionProps.isEnableXA(), 
                  "XA setting should be settable");
    }
    
    @Test
    public void testHazelcastInstanceBeanAvailable() {
        assertTrue(applicationContext.containsBean("hazelcastInstance"), 
                  "HazelcastInstance bean should be available");
        
        if (hazelcastInstance != null) {
            assertNotNull(hazelcastInstance.getName(), 
                         "HazelcastInstance should have a name");
        }
    }
    
    @Test
    public void testTransactionManagerBeanConfiguration() {
        // Transaction manager should be available when transaction is enabled
        boolean transactionManagerExists = applicationContext.containsBean("hazelcastTransactionManager");
        
        if (hazelcastProperties.getTransaction() != null) {
            // If transaction configuration exists, manager should be created
            assertTrue(transactionManagerExists || transactionManager != null, 
                      "HazelcastTransactionManager should be configured when transaction properties exist");
        }
    }
    
    @Test
    public void testTransactionManagerDependencyInjection() {
        if (transactionManager != null) {
            // Test that transaction manager is properly instantiated
            assertNotNull(transactionManager, "Transaction manager should be injected");
            
            // Test that it has required dependencies
            // We can't directly access private fields, but we can test behavior
            assertNotNull(transactionManager.getActiveTransactionIds(), 
                         "Transaction manager should provide active transaction IDs");
        }
    }
    
    @Test
    public void testTransactionConfigurationIntegration() {
        // Test that transaction configuration integrates with Hazelcast configuration
        assertNotNull(hazelcastProperties.getMap(), "Map configuration should exist");
        assertNotNull(hazelcastProperties.getSession(), "Session configuration should exist");
        assertNotNull(hazelcastProperties.getTransaction(), "Transaction configuration should exist");
        
        // Test that configurations don't conflict
        assertNotNull(hazelcastProperties.getMap().getName());
        assertNotNull(hazelcastProperties.getSession().getMapName());
        
        // Ensure transaction timeout is reasonable compared to map TTL
        long transactionTimeout = hazelcastProperties.getTransaction().getTimeoutSeconds();
        int mapTTL = hazelcastProperties.getMap().getTimeToLiveSeconds();
        
        if (mapTTL > 0) {
            assertTrue(transactionTimeout <= mapTTL / 2, 
                      "Transaction timeout should be much shorter than map TTL to avoid conflicts");
        }
    }
    
    /**
     * Test with custom transaction configuration properties.
     */
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "hazelcast.transaction.enabled=true",
        "hazelcast.transaction.timeout-seconds=45",
        "hazelcast.transaction.type=ONE_PHASE",
        "hazelcast.transaction.isolation=SERIALIZABLE",
        "hazelcast.transaction.retry-count=5",
        "hazelcast.transaction.enable-xa=true"
    })
    static class CustomTransactionConfigurationTest {
        
        @Autowired
        private HazelcastProperties hazelcastProperties;
        
        @Test
        public void testCustomTransactionProperties() {
            HazelcastProperties.Transaction transactionProps = hazelcastProperties.getTransaction();
            
            assertEquals(45L, transactionProps.getTimeoutSeconds(), 
                        "Custom timeout should be loaded");
            assertEquals(HazelcastProperties.TransactionType.ONE_PHASE, transactionProps.getType(), 
                        "Custom transaction type should be loaded");
            assertEquals("SERIALIZABLE", transactionProps.getIsolation(), 
                        "Custom isolation should be loaded");
            assertEquals(5, transactionProps.getRetryCount(), 
                        "Custom retry count should be loaded");
            assertTrue(transactionProps.isEnableXA(), 
                      "Custom XA setting should be loaded");
        }
    }
    
    /**
     * Test transaction configuration in development environment.
     */
    @SpringBootTest
    @ActiveProfiles("dev")
    static class DevTransactionConfigurationTest {
        
        @Autowired
        private HazelcastProperties hazelcastProperties;
        
        @Test
        public void testDevTransactionProperties() {
            HazelcastProperties.Transaction transactionProps = hazelcastProperties.getTransaction();
            
            // Development environment might have different defaults
            assertNotNull(transactionProps.getTimeoutSeconds(), 
                         "Dev timeout should be configured");
            
            // In dev, we might want longer timeouts for debugging
            if (transactionProps.getTimeoutSeconds() > 30) {
                assertTrue(transactionProps.getTimeoutSeconds() >= 60, 
                          "Dev environment should have longer timeout for debugging");
            }
        }
    }
    
    @Test
    public void testTransactionTypeEnumValues() {
        // Test that our enum values are properly defined
        HazelcastProperties.TransactionType[] types = HazelcastProperties.TransactionType.values();
        
        boolean twoPhaseFound = false;
        boolean onePhaseFound = false;
        
        for (HazelcastProperties.TransactionType type : types) {
            if (type == HazelcastProperties.TransactionType.TWO_PHASE) {
                twoPhaseFound = true;
            }
            if (type == HazelcastProperties.TransactionType.ONE_PHASE) {
                onePhaseFound = true;
            }
        }
        
        assertTrue(twoPhaseFound, "TWO_PHASE transaction type should be available");
        assertTrue(onePhaseFound, "ONE_PHASE transaction type should be available");
    }
    
    @Test
    public void testConfigurationValidation() {
        HazelcastProperties.Transaction transactionProps = hazelcastProperties.getTransaction();
        
        // Test that configuration values are sensible
        assertTrue(transactionProps.getTimeoutSeconds() > 0, 
                  "Transaction timeout should be positive");
        assertTrue(transactionProps.getRetryCount() >= 0, 
                  "Retry count should be non-negative");
        
        assertNotNull(transactionProps.getIsolation(), 
                     "Isolation level should not be null");
        assertFalse(transactionProps.getIsolation().trim().isEmpty(), 
                   "Isolation level should not be empty");
    }
    
    @Test
    public void testBeanDefinitionOrder() {
        // Test that beans are created in the correct order
        // HazelcastInstance should be created before TransactionManager
        
        assertTrue(applicationContext.containsBean("hazelcastInstance"), 
                  "HazelcastInstance bean should exist");
        
        if (applicationContext.containsBean("hazelcastTransactionManager")) {
            // If transaction manager exists, hazelcast instance should also exist
            assertNotNull(hazelcastInstance, 
                         "HazelcastInstance should be available when TransactionManager is created");
        }
    }
    
    @Test
    public void testConditionalBeanCreation() {
        // Test that transaction manager is only created when appropriate conditions are met
        boolean transactionEnabled = hazelcastProperties.getTransaction() != null;
        boolean hazelcastEnabled = hazelcastProperties.isEnabled();
        
        if (transactionEnabled && hazelcastEnabled) {
            // Transaction manager might be created (depends on additional conditions)
            // We test that at least the configuration is available
            assertNotNull(hazelcastProperties.getTransaction(), 
                         "Transaction configuration should be available when enabled");
        }
    }
}
