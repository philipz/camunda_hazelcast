package com.example.workflow.controller;

import com.example.workflow.controller.dto.ProcessRequest;
import com.example.workflow.controller.dto.ApiCallRequest;
import com.example.workflow.controller.dto.ProcessResponse;
import com.example.workflow.service.ProcessResultAggregator;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private static final Logger logger = LoggerFactory.getLogger(ProcessController.class);

    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private HistoryService historyService;
    
    @Autowired
    private RepositoryService repositoryService;
    
    @Autowired
    private ProcessResultAggregator resultAggregator;

    /**
     * 動態取得指定 process key 中的所有 service task ID
     */
    private java.util.List<String> getServiceTaskIds(String processDefinitionKey) {
        try {
            ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .latestVersion()
                .singleResult();
            
            BpmnModelInstance modelInstance = repositoryService
                .getBpmnModelInstance(processDefinition.getId());
            
            return modelInstance.getModelElementsByType(ServiceTask.class)
                .stream()
                .map(serviceTask -> serviceTask.getId())
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get service task IDs for process: {}", processDefinitionKey, e);
            return java.util.Collections.emptyList();
        }
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeProcess(@RequestBody ProcessRequest request) {
        try {
            // 調試信息：檢查輸入參數
            logger.debug("=== ProcessController Execute Debug Info ===");
            logger.debug("Process type: {}", request.getProcessType());
            logger.debug("Input API calls count: {}", request.getApiCalls() != null ? request.getApiCalls().size() : 0);
            
            if (request.getApiCalls() == null || request.getApiCalls().isEmpty()) {
                throw new IllegalArgumentException("至少需要提供一個 API 呼叫請求");
            }
            
            // 驗證流程類型
            String processType = request.getProcessType();
            if (processType == null || processType.trim().isEmpty()) {
                processType = "sequential"; // 預設為循序處理
            }
            
            if (!"parallel".equals(processType) && !"sequential".equals(processType)) {
                throw new IllegalArgumentException("processType 必須是 'parallel' 或 'sequential'");
            }
            
            // 準備流程變數
            Map<String, Object> variables = new HashMap<>();
            variables.put("apiCalls", request.getApiCalls());
            variables.put("totalApiCalls", request.getApiCalls().size());
            
            if ("parallel".equals(processType)) {
                variables.put("batchSize", Math.min(request.getApiCalls().size(), 100)); // 最大批次大小 100
            }
            
            // 記錄每個 API 呼叫的詳細信息
            for (int i = 0; i < request.getApiCalls().size(); i++) {
                ApiCallRequest apiCall = request.getApiCalls().get(i);
                logger.debug("API Call {}: URL={}, TaskId={}", i, apiCall.getApiUrl(), apiCall.getTaskId());
            }
            
            logger.debug("Variables being passed: {}", variables);
            logger.debug("===================================");

            // 根據流程類型啟動對應的流程實例
            String processKey = "parallel".equals(processType) ? "parallelprocess" : "sequentialprocess";
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey, variables);

            // 檢查流程狀態
            logger.debug("Process Instance ID: {}", processInstance.getId());
            logger.debug("Process Instance ended: {}", processInstance.isEnded());
            
            // 取得流程變數：根據流程是否結束選擇不同的方法
            Map<String, Object> processVariables = new HashMap<>();
            if (processInstance.isEnded()) {
                // 流程已結束，從歷史記錄中取得變數
                List<HistoricVariableInstance> historicVariables = historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .list();
                
                for (HistoricVariableInstance variable : historicVariables) {
                    processVariables.put(variable.getName(), variable.getValue());
                }
            } else {
                // 流程尚未結束，從運行時取得變數
                processVariables = runtimeService.getVariables(processInstance.getId());
            }
            logger.debug("Variables after execution: {}", processVariables);
            
            // 聚合執行結果
            ProcessResponse processResponse = resultAggregator.aggregateResults(processInstance.getId(), 
                processVariables, request.getApiCalls(), processType);
            
            // 轉換為 Map 格式以保持向後相容性
            Map<String, Object> response = convertToMap(processResponse);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("流程執行失敗", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "流程執行失敗");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 將 ProcessResponse 轉換為 Map 格式以保持向後相容性
     */
    private Map<String, Object> convertToMap(ProcessResponse processResponse) {
        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processResponse.getProcessInstanceId());
        response.put("processType", processResponse.getProcessType());
        response.put("overallStatus", processResponse.getOverallStatus());
        response.put("successCount", processResponse.getSuccessCount());
        response.put("totalCount", processResponse.getTotalCount());
        response.put("results", processResponse.getResults());
        
        // 添加平行處理專用欄位（如果存在）
        if (processResponse.getCompletedInstances() != null) {
            response.put("completedInstances", processResponse.getCompletedInstances());
        }
        if (processResponse.getTotalInstances() != null) {
            response.put("totalInstances", processResponse.getTotalInstances());
        }
        if (processResponse.getCompletionRate() != null) {
            response.put("completionRate", processResponse.getCompletionRate());
        }
        
        return response;
    }

}