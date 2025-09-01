# Hazelcast 分散式事務演示指南

本文檔說明如何使用現有的 `parallelprocess.bpmn` 工作流程來演示 Hazelcast 分散式事務功能。

## 工作流程概述

`parallelprocess.bpmn` 工作流程包含：
- **RestServiceDelegate 服務任務** - 現在具有事務功能
- **多實例循環特性** - 並行處理多個 API 調用
- **事務協調** - 確保所有 API 調用在單一事務中執行

## 事務演示場景

### 場景 1：成功的分散式事務

所有並行 API 調用都成功，事務正常提交：

```json
{
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"action\": \"create_user\", \"userId\": 1}"
    },
    {
      "apiUrl": "https://httpbin.org/post", 
      "payload": "{\"action\": \"create_profile\", \"userId\": 1}"
    },
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"action\": \"send_welcome_email\", \"userId\": 1}"
    }
  ]
}
```

**預期行為：**
- 所有 3 個 API 調用在同一個分散式事務中執行
- 所有 API 回應都事務性地存儲在 Hazelcast 中
- 事務成功提交，所有操作持久化

### 場景 2：事務回滾（API 失敗）

其中一個 API 調用失敗，導致整個事務回滾：

```json
{
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"action\": \"create_user\", \"userId\": 2}"
    },
    {
      "apiUrl": "https://httpbin.org/status/500",
      "payload": "{\"action\": \"create_profile\", \"userId\": 2}"
    },
    {
      "apiUrl": "https://httpbin.org/post",
      "payload": "{\"action\": \"send_welcome_email\", \"userId\": 2}"
    }
  ]
}
```

**預期行為：**
- 第二個 API 調用失敗（500 錯誤）
- 事務自動回滾
- 第一個和第三個 API 調用的結果都被撤銷
- Hazelcast 中沒有留下不一致的數據

### 場景 3：事務超時

API 調用超時，觸發事務超時處理：

```json
{
  "apiCalls": [
    {
      "apiUrl": "https://httpbin.org/delay/35",
      "payload": "{\"action\": \"slow_operation\", \"userId\": 3}"
    }
  ]
}
```

**預期行為：**
- API 調用超過事務超時限制（默認 30 秒）
- 事務管理器觸發自動回滾
- 清理所有相關資源

## 啟動演示

### 1. 準備測試數據

```bash
# 啟動應用程序
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 通過 REST API 啟動工作流程
curl -X POST http://localhost:8080/engine-rest/process-definition/key/parallelprocess/start \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "apiCalls": {
        "value": [
          {
            "apiUrl": "https://httpbin.org/post",
            "payload": "{\"action\": \"create_user\", \"userId\": 1}"
          },
          {
            "apiUrl": "https://httpbin.org/post", 
            "payload": "{\"action\": \"create_profile\", \"userId\": 1}"
          }
        ]
      }
    }
  }'
```

### 2. 監控事務執行

查看應用程序日誌中的事務相關信息：

```
INFO  - Transaction abc-123 started for process process-456
INFO  - Making HTTP call within transaction abc-123 to: https://httpbin.org/post
INFO  - Successfully stored API response in transaction abc-123 for activity rest-api
INFO  - Transaction abc-123 committed successfully in 150ms
```

### 3. 驗證 Hazelcast 數據

事務成功後，檢查 Hazelcast 中存儲的數據：

```java
// 通過 Hazelcast 客戶端查看存儲的事務數據
IMap<String, Object> transactionData = hazelcastInstance.getMap("transaction-data");
// 查看 API 回應數據
```

## 事務功能特性演示

### 1. ACID 屬性

- **原子性（Atomicity）**: 所有 API 調用要麼全部成功，要麼全部失敗
- **一致性（Consistency）**: Hazelcast 數據始終保持一致狀態
- **隔離性（Isolation）**: 並發事務不會相互干擾
- **持久性（Durability）**: 提交的事務數據在 Hazelcast 集群中持久化

### 2. 錯誤處理

- **自動重試**: 暫時性錯誤（網絡問題）會自動重試
- **死鎖檢測**: 自動檢測並解決死鎖情況
- **超時處理**: 事務超時時自動回滾

### 3. 監控和指標

- **事務持續時間**: 記錄每個事務的執行時間
- **成功率**: 追蹤事務成功/失敗率
- **性能指標**: 監控事務吞吐量和延遲

## 配置選項

### application.yaml 中的事務配置

```yaml
hazelcast:
  transaction:
    enabled: true
    timeout-seconds: 30
    type: TWO_PHASE
    isolation: READ_COMMITTED
    retry-count: 3
    deadlock-detection:
      enabled: true
      timeout-seconds: 10
    monitoring:
      enabled: true
      log-transaction-details: true
```

### 環境特定設置

#### 開發環境 (application-dev.yaml)
```yaml
hazelcast:
  transaction:
    timeout-seconds: 60  # 更長的超時以便調試
    retry-count: 5       # 更多重試次數
    monitoring:
      log-transaction-details: true  # 詳細日誌
```

#### 生產環境
```yaml
hazelcast:
  transaction:
    timeout-seconds: 30
    retry-count: 3
    monitoring:
      log-transaction-details: false  # 減少日誌輸出
```

## 故障排除

### 常見問題

1. **事務超時**
   - 增加 `timeout-seconds` 配置
   - 檢查 API 回應時間
   - 優化網絡連接

2. **死鎖情況**
   - 系統會自動檢測並重試
   - 檢查並發訪問模式
   - 調整重試策略

3. **記憶體使用**
   - 監控 Hazelcast 集群記憶體
   - 調整事務數據 TTL
   - 優化事務大小

### 日誌級別配置

```yaml
logging:
  level:
    com.example.workflow.transaction: DEBUG
    com.example.workflow.tasks.RestServiceDelegate: DEBUG
```

## 性能考慮

### 最佳實踐

1. **事務大小**: 保持事務操作數量適中（建議 ≤ 10 個操作）
2. **超時設置**: 根據 API 回應時間設置合理的超時
3. **重試策略**: 針對不同錯誤類型設置適當的重試策略
4. **監控**: 啟用性能監控以追蹤事務健康狀況

### 預期性能指標

- **簡單事務（2-3 操作）**: < 100ms
- **複雜事務（5+ 操作）**: < 500ms
- **吞吐量**: > 100 事務/秒
- **成功率**: > 99%

通過這個演示，您可以完整地驗證 Hazelcast 分散式事務功能在真實 Camunda 工作流程中的表現。
