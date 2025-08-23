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

@Component("putServiceDelegate")
public class putServiceDelegate implements JavaDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(putServiceDelegate.class);
    
    @Autowired
    private HazelcastInstance hazelcastInstance;
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String activityId = execution.getCurrentActivityId();
        
        try {
            // Store workflow data in Hazelcast
            IMap<String, Object> map = hazelcastInstance.getMap("myMap");
            
            // Use process instance ID as key for data isolation
            String processInstanceId = execution.getProcessInstanceId();
            String key = processInstanceId + "_" + activityId;
            
            // Store some sample data - in real scenarios this would come from process variables
            Object value = execution.getVariable("data");
            if (value == null) {
                value = "default_value_from_" + activityId;
            }
            
            map.put(key, value);
            logger.info("Stored data in Hazelcast: key={}, value={}", key, value);
            
            // Set the key as process variable for retrieval by other tasks
            execution.setVariable("hazelcast_key", key);
            
        } catch (Exception e) {
            logger.error("Error storing data in Hazelcast", e);
            throw new BpmnError("HAZELCAST_PUT_ERROR", "Failed to store data in Hazelcast: " + e.getMessage());
        }
    }
}