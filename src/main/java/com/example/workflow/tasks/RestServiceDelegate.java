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

@Component("restServiceDelegate")
public class RestServiceDelegate implements JavaDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(RestServiceDelegate.class);
    
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String activityId = execution.getCurrentActivityId();
        
        // 調試信息：檢查所有可用的變數
        logger.debug("=== RestServiceDelegate Debug Info ===");
        logger.debug("Process Instance ID: {}", execution.getProcessInstanceId());
        logger.debug("Activity ID: {}", activityId);
        logger.debug("All Variables: {}", execution.getVariables());
        
        // 使用 activityId 作為前綴來區分不同 service task 的變數
        String apiUrlVar = activityId + "_apiUrl";
        String payloadVar = activityId + "_requestPayload";
        
        // 檢查多實例循環變數（優先級最高）
        Object apiCallObj = execution.getVariable("apiCall");
        String apiUrl = null;
        String payload = null;
        
        if (apiCallObj != null) {
            // 這是多實例循環中的 ApiCallRequest 對象
            logger.debug("Found apiCall object: {}", apiCallObj);
            
            // 嘗試通過反射獲取字段值
            try {
                java.lang.reflect.Field apiUrlField = apiCallObj.getClass().getDeclaredField("apiUrl");
                apiUrlField.setAccessible(true);
                apiUrl = (String) apiUrlField.get(apiCallObj);
                
                java.lang.reflect.Field payloadField = apiCallObj.getClass().getDeclaredField("payload");
                payloadField.setAccessible(true);
                payload = (String) payloadField.get(apiCallObj);
                
                logger.debug("Extracted from apiCall - apiUrl: {}, payload: {}", apiUrl, payload);
            } catch (Exception e) {
                logger.warn("Failed to extract values from apiCall object: {}", e.getMessage());
            }
        }
        
        // 如果多實例變數不可用，則嘗試使用 task-specific 變數（向後兼容）
        if (apiUrl == null) {
            apiUrl = (String) execution.getVariable(apiUrlVar);
            if (apiUrl == null) {
                apiUrl = (String) execution.getVariable("apiUrl");
            }
        }
        
        if (payload == null) {
            payload = (String) execution.getVariable(payloadVar);
            if (payload == null) {
                payload = (String) execution.getVariable("requestPayload");
            }
        }
        
        logger.debug("Looking for variables: {} and {}", apiUrlVar, payloadVar);
        logger.debug("apiUrl: {}", apiUrl);
        logger.debug("payload: {}", payload);
        logger.debug("======================================");
        
        // 檢查必要的變數是否存在
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new Exception("apiUrl variable is required but not provided. " +
                "Expected variable names: '" + apiUrlVar + "' or 'apiUrl'");
        }
        if (payload == null) {
            payload = "{}"; // 使用空 JSON 作為預設值
        }
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .timeout(Duration.ofSeconds(30))
            .build();
            
        try {
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            handleResponse(execution, response);
            
        } catch (Exception e) {
            throw new Exception("REST call failed: " + e.getMessage());
        }
    }
    
    private void handleResponse(DelegateExecution execution, HttpResponse<String> response) 
            throws BpmnError {
        String activityId = execution.getCurrentActivityId();
        int statusCode = response.statusCode();
        String responseBody = response.body();
        
        if (statusCode == 200 || statusCode == 201) {
            // 使用 activityId 作為前綴設置 task-specific 回應變數
            String responseVar = activityId + "_responseData";
            String statusVar = activityId + "_status";
            
            execution.setVariable(responseVar, responseBody);
            execution.setVariable(statusVar, "SUCCESS");
            
            // 也設置通用變數以保持向後兼容性
            execution.setVariable("responseData", responseBody);
            execution.setVariable("status", "SUCCESS");
            
            logger.debug("Set response variables: {} = {}, {} = SUCCESS", 
                responseVar, responseBody, statusVar);
        } else if (statusCode >= 400 && statusCode < 500) {
            throw new BpmnError("CLIENT_ERROR", "API returned client error: " + statusCode);
        } else {
            throw new RuntimeException("API system error: " + statusCode);
        }
    }
}