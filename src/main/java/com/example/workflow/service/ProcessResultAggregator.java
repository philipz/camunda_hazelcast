package com.example.workflow.service;

import com.example.workflow.controller.dto.ApiCallRequest;
import com.example.workflow.controller.dto.ProcessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程結果聚合器
 * 負責將 Camunda 流程執行結果聚合為統一的回應格式
 */
@Component
public class ProcessResultAggregator {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessResultAggregator.class);

    /**
     * 聚合執行結果 (支援平行與循序)
     * 
     * @param processInstanceId 流程實例 ID
     * @param processVariables 流程變數
     * @param originalRequests 原始請求列表
     * @param processType 流程類型
     * @return 聚合後的回應物件
     */
    public ProcessResponse aggregateResults(String processInstanceId, 
            Map<String, Object> processVariables, List<ApiCallRequest> originalRequests, String processType) {
        
        ProcessResponse response = new ProcessResponse();
        response.setProcessInstanceId(processInstanceId);
        response.setProcessType(processType);
        
        // 取得結果集合 - 根據流程類型處理
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) processVariables.get("results");
        
        if (results == null || results.isEmpty()) {
            // 如果沒有結果集合，嘗試從個別變數中取得結果
            results = extractIndividualResults(processVariables, originalRequests, processType);
        }
        
        // 計算整體狀態
        int successCount = 0;
        int totalCount = results.size();
        
        for (Map<String, Object> result : results) {
            String status = (String) result.get("status");
            if ("SUCCESS".equals(status)) {
                successCount++;
            }
        }
        
        String overallStatus = determineOverallStatus(successCount, totalCount);
        
        response.setOverallStatus(overallStatus);
        response.setSuccessCount(successCount);
        response.setTotalCount(totalCount);
        response.setResults(results);
        
        // 添加執行統計信息（主要針對平行處理）
        addExecutionStatistics(response, processVariables);
        
        return response;
    }

    /**
     * 判斷整體執行狀態
     */
    private String determineOverallStatus(int successCount, int totalCount) {
        if (successCount == totalCount) {
            return "SUCCESS";
        } else if (successCount > 0) {
            return "PARTIAL_SUCCESS";
        } else {
            return "FAILURE";
        }
    }

    /**
     * 添加執行統計信息
     */
    private void addExecutionStatistics(ProcessResponse response, Map<String, Object> processVariables) {
        Integer nrOfCompletedInstances = (Integer) processVariables.get("nrOfCompletedInstances");
        Integer nrOfInstances = (Integer) processVariables.get("nrOfInstances");
        
        if (nrOfCompletedInstances != null && nrOfInstances != null) {
            response.setCompletedInstances(nrOfCompletedInstances);
            response.setTotalInstances(nrOfInstances);
            response.setCompletionRate(
                nrOfInstances > 0 ? (double) nrOfCompletedInstances / nrOfInstances : 0.0);
        }
    }

    /**
     * 從個別變數中提取結果（備用方法）
     */
    private List<Map<String, Object>> extractIndividualResults(Map<String, Object> processVariables, 
            List<ApiCallRequest> originalRequests, String processType) {
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        // 對於多實例循環，我們需要查找 rest-api 相關的變數
        // 因為所有多實例都使用同一個 service task "rest-api"
        String responseData = (String) processVariables.get("rest-api_responseData");
        String status = (String) processVariables.get("rest-api_status");
        
        // 如果找不到 task-specific 變數，嘗試通用變數
        if (responseData == null) {
            responseData = (String) processVariables.get("responseData");
        }
        if (status == null) {
            status = (String) processVariables.get("status");
        }
        
        // 由於多實例循環，我們為每個原始請求創建一個結果條目
        for (int i = 0; i < originalRequests.size(); i++) {
            Map<String, Object> result = new HashMap<>();
            
            result.put("index", i);
            result.put("apiUrl", originalRequests.get(i).getApiUrl());
            result.put("status", status != null ? status : "UNKNOWN");
            result.put("responseData", responseData);
            
            results.add(result);
        }
        
        // 如果沒有找到任何結果，嘗試回退到索引模式
        if (responseData == null && status == null) {
            results.clear();
            results = extractResultsWithIndexFallback(processVariables, originalRequests);
        }
        
        return results;
    }

    /**
     * 使用索引回退模式提取結果
     */
    private List<Map<String, Object>> extractResultsWithIndexFallback(
            Map<String, Object> processVariables, List<ApiCallRequest> originalRequests) {
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (int i = 0; i < originalRequests.size(); i++) {
            Map<String, Object> result = new HashMap<>();
            
            // 嘗試多種可能的變數命名模式
            String[] possibleKeys = {
                "result_" + i,
                "apiResult_" + i,
                "responseData_" + i,
                "item_" + i + "_responseData"
            };
            
            String fallbackResponseData = null;
            String fallbackStatus = null;
            
            for (String key : possibleKeys) {
                if (processVariables.containsKey(key)) {
                    fallbackResponseData = (String) processVariables.get(key);
                    break;
                }
            }
            
            String[] possibleStatusKeys = {
                "status_" + i,
                "apiStatus_" + i,
                "item_" + i + "_status"
            };
            
            for (String key : possibleStatusKeys) {
                if (processVariables.containsKey(key)) {
                    fallbackStatus = (String) processVariables.get(key);
                    break;
                }
            }
            
            result.put("index", i);
            result.put("apiUrl", originalRequests.get(i).getApiUrl());
            result.put("status", fallbackStatus != null ? fallbackStatus : "UNKNOWN");
            result.put("responseData", fallbackResponseData);
            
            results.add(result);
        }
        
        logger.debug("Extracted {} results using index fallback mode", results.size());
        return results;
    }
}