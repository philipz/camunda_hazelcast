package com.example.workflow.tasks;

import com.example.workflow.transaction.TransactionalServiceDelegate;
import com.example.workflow.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionalMap;
import org.springframework.stereotype.Component;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@Component("restServiceDelegate")
public class RestServiceDelegate extends TransactionalServiceDelegate implements JavaDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(RestServiceDelegate.class);
    
    @Override
    protected void executeInTransaction(DelegateExecution execution, TransactionContext transactionContext) throws Exception {
        String activityId = execution.getCurrentActivityId();
        
        // 調試信息：檢查所有可用的變數
        logger.debug("=== RestServiceDelegate Transactional Debug Info ===");
        logger.debug("Process Instance ID: {}", execution.getProcessInstanceId());
        logger.debug("Activity ID: {}", activityId);
        logger.debug("Transaction ID: {}", transactionContext.transactionId());
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
            
            // 首先嘗試作為 Map 處理（測試中的情況）
            if (apiCallObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> apiCallMap = (java.util.Map<String, String>) apiCallObj;
                apiUrl = apiCallMap.get("apiUrl");
                payload = apiCallMap.get("payload");
                logger.debug("Extracted from apiCall Map - apiUrl: {}, payload: {}", apiUrl, payload);
            } else {
                // 嘗試通過反射獲取字段值（對象的情況）
                try {
                    java.lang.reflect.Field apiUrlField = apiCallObj.getClass().getDeclaredField("apiUrl");
                    apiUrlField.setAccessible(true);
                    apiUrl = (String) apiUrlField.get(apiCallObj);
                    
                    java.lang.reflect.Field payloadField = apiCallObj.getClass().getDeclaredField("payload");
                    payloadField.setAccessible(true);
                    payload = (String) payloadField.get(apiCallObj);
                    
                    logger.debug("Extracted from apiCall Object - apiUrl: {}, payload: {}", apiUrl, payload);
                } catch (Exception e) {
                    logger.warn("Failed to extract values from apiCall object: {}", e.getMessage());
                }
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
            logger.info("Making HTTP call within transaction {} to: {}", 
                       transactionContext.transactionId(), apiUrl);
            
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            // Handle response and store in Hazelcast transactionally
            handleTransactionalResponse(execution, response, transactionContext);
            
        } catch (BpmnError e) {
            // Let BpmnError propagate up to Camunda without wrapping
            logger.warn("BPMN error in transaction {}: {}", 
                       transactionContext.transactionId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("REST call failed within transaction {}: {}", 
                        transactionContext.transactionId(), e.getMessage(), e);
            throw new Exception("REST call failed within transaction: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle HTTP response transactionally by storing data in Hazelcast.
     * This ensures that API response data is consistent with transaction state.
     */
    private void handleTransactionalResponse(DelegateExecution execution, 
                                           HttpResponse<String> response, 
                                           TransactionContext transactionContext) throws Exception {
        String activityId = execution.getCurrentActivityId();
        int statusCode = response.statusCode();
        String responseBody = response.body();
        String transactionId = transactionContext.transactionId();
        
        logger.debug("Handling HTTP response {} within transaction {}", statusCode, transactionId);
        
        if (statusCode == 200 || statusCode == 201) {
            // Store response data in Hazelcast transactionally
            storeResponseDataTransactionally(execution, responseBody, transactionContext);
            
            // Set execution variables for immediate use
            String responseVar = activityId + "_responseData";
            String statusVar = activityId + "_status";
            
            execution.setVariable(responseVar, responseBody);
            execution.setVariable(statusVar, "SUCCESS");
            execution.setVariable("responseData", responseBody);
            execution.setVariable("status", "SUCCESS");
            execution.setVariable("transactionId", transactionId);
            
            logger.info("Successfully stored API response in transaction {} for activity {}", 
                       transactionId, activityId);
            
        } else if (statusCode >= 400 && statusCode < 500) {
            logger.warn("API client error {} in transaction {}", statusCode, transactionId);
            throw new BpmnError("CLIENT_ERROR", 
                "API returned client error: " + statusCode + " in transaction: " + transactionId);
        } else {
            logger.error("API system error {} in transaction {}", statusCode, transactionId);
            throw new RuntimeException(
                "API system error: " + statusCode + " in transaction: " + transactionId);
        }
    }
    
    /**
     * Store API response data transactionally in Hazelcast.
     * This method ensures that API response data is persisted atomically
     * with other transaction operations.
     */
    private void storeResponseDataTransactionally(DelegateExecution execution, 
                                                 String responseBody, 
                                                 TransactionContext transactionContext) throws Exception {
        String activityId = execution.getCurrentActivityId();
        String processInstanceId = execution.getProcessInstanceId();
        String transactionId = transactionContext.transactionId();
        
        try {
            // Get transactional map for storing API response data
            TransactionalMap<String, Object> transactionalMap = 
                getTransactionalMap("transaction-data", transactionContext);
            
            // Create composite key for the response data
            String responseKey = String.format("api-response:%s:%s:%s", 
                processInstanceId, activityId, transactionId);
            logger.debug("responseKey: {}", responseKey);
            // Create response metadata
            ApiResponseData responseData = new ApiResponseData(
                responseBody,
                activityId,
                processInstanceId,
                transactionId,
                Instant.now()
            );
            
            // Store response data transactionally
            transactionalMap.put(responseKey, responseData);
            
            logger.debug("Stored API response data transactionally with key: {}", responseKey);
            
            // Also store a reference for easier lookup
            String lookupKey = String.format("process-responses:%s", processInstanceId);
            TransactionalMap<String, Object> lookupMap = 
                getTransactionalMap("transaction-data", transactionContext);
            lookupMap.put(lookupKey + ":" + activityId, responseKey);
            logger.debug("lookupKey: {}", lookupKey + ":" + activityId);
            
        } catch (Exception e) {
            logger.error("Failed to store API response transactionally for transaction {}: {}", 
                        transactionId, e.getMessage(), e);
            throw new Exception("Failed to store API response transactionally: " + e.getMessage(), e);
        }
    }
    
    /**
     * Data class for storing API response information transactionally.
     */
    private static class ApiResponseData {
        private final String responseBody;
        private final String activityId;
        private final String processInstanceId;
        private final String transactionId;
        private final long timestampMillis;
        
        public ApiResponseData(String responseBody, String activityId, 
                              String processInstanceId, String transactionId, Instant timestamp) {
            this.responseBody = responseBody;
            this.activityId = activityId;
            this.processInstanceId = processInstanceId;
            this.transactionId = transactionId;
            this.timestampMillis = timestamp.toEpochMilli();
        }
        
        // Getters
        public String getResponseBody() { return responseBody; }
        public String getActivityId() { return activityId; }
        public String getProcessInstanceId() { return processInstanceId; }
        public String getTransactionId() { return transactionId; }
        public long getTimestampMillis() { return timestampMillis; }
        public Instant getTimestamp() { return Instant.ofEpochMilli(timestampMillis); }
        
        @Override
        public String toString() {
            return String.format("ApiResponseData{transactionId='%s', activityId='%s', timestamp=%s}", 
                                transactionId, activityId, Instant.ofEpochMilli(timestampMillis));
        }
    }
}