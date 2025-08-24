package com.example.workflow.tasks;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

@Component("getServiceDelegate")
public class getServiceDelegate implements JavaDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(getServiceDelegate.class);
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String activityId = execution.getCurrentActivityId();
        
        try {
            // Retrieve workflow data from Hazelcast
            IMap<String, Object> map = hazelcastInstance.getMap("myMap");
            
            // Try to get the key from process variables (set by putServiceDelegate)
            final String key = (String) execution.getVariable("hazelcast_key");
            
            Object value = map.get(key);
            
            if (value != null) {
                logger.info("Retrieved data from Hazelcast: key={}, value={}", key, value);
                // Store retrieved value as process variable for use by subsequent tasks
                execution.setVariable("retrieved_data", value);
                // 使用 deleteAsync 並處理非同步結果
                map.deleteAsync(key).thenAccept(deleted -> {
                    if (deleted) {
                        logger.info("Successfully deleted key: {}", key);
                    } else {
                        logger.warn("Failed to delete key: {}", key);
                    }
                }).exceptionally(throwable -> {
                    logger.error("Error deleting key: {}", key, throwable);
                    return null;
                });
            } else {
                logger.warn("No data found in Hazelcast for key: {}", key);
                execution.setVariable("retrieved_data", null);
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving data from Hazelcast", e);
            throw new BpmnError("HAZELCAST_GET_ERROR", "Failed to retrieve data from Hazelcast: " + e.getMessage());
        }
    }
}