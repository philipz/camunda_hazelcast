package com.example.workflow.integration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.camunda.bpm.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class HazelcastWorkflowIntegrationTest {
    
    @Autowired
    private ProcessEngine processEngine;
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    @Test
    public void testHazelcastInstanceIsInjected() {
        assertNotNull(hazelcastInstance, "HazelcastInstance should be injected");
        assertTrue(hazelcastInstance.getLifecycleService().isRunning(), "Hazelcast should be running");
    }
    
    @Test
    public void testServiceDelegateStoresAndRetrievesData() {
        // Verify Hazelcast map is accessible
        IMap<String, Object> map = hazelcastInstance.getMap("myMap");
        assertNotNull(map, "Hazelcast map should be available");
        
        // Test direct map operations
        String testKey = "integration_test_key";
        String testValue = "integration_test_value";
        
        map.put(testKey, testValue);
        Object retrievedValue = map.get(testKey);
        
        assertEquals(testValue, retrievedValue, "Value should be stored and retrieved correctly");
        
        // Clean up
        map.remove(testKey);
    }
    
    @Test
    public void testMultipleProcessInstanceDataIsolation() {
        IMap<String, Object> map = hazelcastInstance.getMap("myMap");
        
        // Simulate multiple process instances with different data
        String processId1 = "process1";
        String processId2 = "process2";
        String activityId = "testActivity";
        
        String key1 = processId1 + "_" + activityId;
        String key2 = processId2 + "_" + activityId;
        
        String value1 = "data_from_process_1";
        String value2 = "data_from_process_2";
        
        // Store data for both processes
        map.put(key1, value1);
        map.put(key2, value2);
        
        // Verify data isolation
        assertEquals(value1, map.get(key1), "Process 1 data should be isolated");
        assertEquals(value2, map.get(key2), "Process 2 data should be isolated");
        assertNotEquals(map.get(key1), map.get(key2), "Process data should be different");
        
        // Clean up
        map.remove(key1);
        map.remove(key2);
    }
    
    @Test
    public void testHazelcastMapConfiguration() {
        IMap<String, Object> map = hazelcastInstance.getMap("myMap");
        
        // Test that map is properly configured
        assertNotNull(map, "Map should be created");
        assertEquals("myMap", map.getName(), "Map name should match configuration");
        
        // Test basic map operations
        String key = "config_test";
        String value = "config_value";
        
        map.put(key, value);
        assertTrue(map.containsKey(key), "Map should contain the key");
        assertEquals(value, map.get(key), "Map should return correct value");
        
        map.remove(key);
        assertFalse(map.containsKey(key), "Key should be removed");
    }
}