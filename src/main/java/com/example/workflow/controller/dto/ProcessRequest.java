package com.example.workflow.controller.dto;

import java.util.List;

/**
 * 流程執行請求物件
 * 支援循序（sequential）和平行（parallel）處理模式
 */
public class ProcessRequest {
    
    private String processType; // "parallel" 或 "sequential"
    private List<ApiCallRequest> apiCalls;

    /**
     * 取得流程類型
     * @return 流程類型 ("parallel" 或 "sequential")
     */
    public String getProcessType() {
        return processType;
    }

    /**
     * 設定流程類型
     * @param processType 流程類型 ("parallel" 或 "sequential")
     */
    public void setProcessType(String processType) {
        this.processType = processType;
    }

    /**
     * 取得 API 呼叫列表
     * @return API 呼叫請求列表
     */
    public List<ApiCallRequest> getApiCalls() {
        return apiCalls;
    }

    /**
     * 設定 API 呼叫列表
     * @param apiCalls API 呼叫請求列表
     */
    public void setApiCalls(List<ApiCallRequest> apiCalls) {
        this.apiCalls = apiCalls;
    }

    @Override
    public String toString() {
        return "ProcessRequest{" +
                "processType='" + processType + '\'' +
                ", apiCalls=" + (apiCalls != null ? apiCalls.size() : 0) + " items" +
                '}';
    }
}