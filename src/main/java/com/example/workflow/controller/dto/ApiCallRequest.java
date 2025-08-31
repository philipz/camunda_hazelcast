package com.example.workflow.controller.dto;

import java.io.Serializable;

/**
 * 單一 API 呼叫請求物件
 * 支援自定義 taskId 和 payload
 */
public class ApiCallRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String apiUrl;
    private String payload;
    private String taskId; // 可選，用於指定特定的 service task

    /**
     * 取得 API URL
     * @return API 端點 URL
     */
    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * 設定 API URL
     * @param apiUrl API 端點 URL
     */
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /**
     * 取得請求 payload
     * @return JSON 格式的請求資料
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 設定請求 payload
     * @param payload JSON 格式的請求資料
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * 取得自定義任務 ID
     * @return 任務 ID，可能為 null
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 設定自定義任務 ID
     * @param taskId 任務 ID，用於指定特定的 service task
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public String toString() {
        return "ApiCallRequest{" +
                "apiUrl='" + apiUrl + '\'' +
                ", payload='" + (payload != null ? payload.length() + " chars" : "null") + '\'' +
                ", taskId='" + taskId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ApiCallRequest that = (ApiCallRequest) o;

        if (apiUrl != null ? !apiUrl.equals(that.apiUrl) : that.apiUrl != null) return false;
        if (payload != null ? !payload.equals(that.payload) : that.payload != null) return false;
        return taskId != null ? taskId.equals(that.taskId) : that.taskId == null;
    }

    @Override
    public int hashCode() {
        int result = apiUrl != null ? apiUrl.hashCode() : 0;
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        result = 31 * result + (taskId != null ? taskId.hashCode() : 0);
        return result;
    }
}