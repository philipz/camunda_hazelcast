package com.example.workflow.tasks;

import org.springframework.stereotype.Component;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

@Component("putServiceDelegate")
public class putServiceDelegate implements JavaDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(putServiceDelegate.class);
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String activityId = execution.getCurrentActivityId();
        
        // 設計儲存Hazelcast的變數
        IMap<String, Object> map = hazelcastInstance.getMap("myMap");
        map.put("key", "value");
    }
}