package com.example.workflow.controller.dto;

import java.util.List;
import java.util.Map;

/**
 * 流程執行回應物件
 * 統一的回應格式，支援循序和平行處理結果
 */
public class ProcessResponse {
    
    private String processInstanceId;
    private String processType;
    private String overallStatus;
    private Integer successCount;
    private Integer totalCount;
    private List<Map<String, Object>> results;
    
    // 平行處理專用欄位
    private Integer completedInstances;
    private Integer totalInstances;
    private Double completionRate;

    /**
     * 取得流程實例 ID
     */
    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    /**
     * 取得流程類型
     */
    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    /**
     * 取得整體執行狀態
     * @return SUCCESS, PARTIAL_SUCCESS, 或 FAILURE
     */
    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    /**
     * 取得成功數量
     */
    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    /**
     * 取得總數量
     */
    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * 取得執行結果列表
     */
    public List<Map<String, Object>> getResults() {
        return results;
    }

    public void setResults(List<Map<String, Object>> results) {
        this.results = results;
    }

    /**
     * 取得完成的實例數量（平行處理專用）
     */
    public Integer getCompletedInstances() {
        return completedInstances;
    }

    public void setCompletedInstances(Integer completedInstances) {
        this.completedInstances = completedInstances;
    }

    /**
     * 取得總實例數量（平行處理專用）
     */
    public Integer getTotalInstances() {
        return totalInstances;
    }

    public void setTotalInstances(Integer totalInstances) {
        this.totalInstances = totalInstances;
    }

    /**
     * 取得完成率（平行處理專用）
     */
    public Double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(Double completionRate) {
        this.completionRate = completionRate;
    }

    @Override
    public String toString() {
        return "ProcessResponse{" +
                "processInstanceId='" + processInstanceId + '\'' +
                ", processType='" + processType + '\'' +
                ", overallStatus='" + overallStatus + '\'' +
                ", successCount=" + successCount +
                ", totalCount=" + totalCount +
                ", results=" + (results != null ? results.size() + " items" : "null") +
                ", completedInstances=" + completedInstances +
                ", totalInstances=" + totalInstances +
                ", completionRate=" + completionRate +
                '}';
    }
}