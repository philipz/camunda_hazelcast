# Spring Boot 4.0.8 → 4.1.1 相容性評估與升級（Issue #35）

- Issue：`#35`（`agent-update-deps`）
- 目標 repo：`philipz/camunda_hazelcast`（factory trunk：`software-factory`）
- 評估日期：2026-09
- 本次變更：`pom.xml` 的 `org.springframework.boot:spring-boot-dependencies` import `4.0.8` → `4.1.1`
  （唯一程式變更）。`spring-boot-maven-plugin` 的版本升級由既有 PR #34 另行處理，不在本工作項範圍。

## 1. 結論摘要

**升級可行（經本機全量實測支援）；惟 Operaton 官方支援矩陣尚未將 Operaton 2.1.4 與 Spring Boot 4.1.1 列為認證組合。**

- Operaton 2.1.4 的 `operaton-root` POM 宣告 `<version.spring-boot>4.0.8</version.spring-boot>`、
  `<version.spring>7.0.9</version.spring>`；Operaton 官方「Spring Boot Version Compatibility」表將
  2.1.4 綁定 `4.0.8`（單一版本，非範圍）。
- Spring Boot `4.0.8` 與 `4.1.1` 兩個 BOM 的 `spring-framework.version` **同為 `7.0.9`（不變）**，
  這是與 Operaton engine 最相關的傳遞相依；差異集中在 spring-security 與 spring-session 等
  （見 §3）。因此本次屬「同一 Spring Framework 版本線上的 Boot patch/minor 升級」。
- 本機以 JDK 21 實測：`mvn -B -ntp clean verify` → **BUILD SUCCESS，Tests run: 32, Failures: 0,
  Errors: 0, Skipped: 0**，**無需任何原始碼、設定或相依座標修正**。
- 上游佐證：dependabot PR #18（同一行 `spring-boot-dependencies` `4.0.8` → `4.1.1`）在其 base
  分支（`main`，內容與 `software-factory` 相同）的 CI `build-test`（`./mvnw -B -ntp clean verify`）
  已為綠燈。

## 2. 官方支援範圍證據

來源：[Operaton — Spring Boot Version Compatibility](https://docs.operaton.org/docs/documentation/user-guide/spring-boot-integration/version-compatibility/)
（表格節錄）

| Operaton version | Spring Boot version |
|---|---|
| 2.1.2 | 4.0.7 |
| 2.1.3 | 4.0.7 |
| **2.1.4** | **4.0.8** |

官方原文：「Only these default combinations are recommended (and supported) by Operaton. Other
combinations must be thoroughly tested before being used in production.」

`operaton-root:2.1.4` POM（Maven Central）：

```xml
<version.java>17</version.java>
<version.spring>7.0.9</version.spring>
<version.spring-boot>4.0.8</version.spring-boot>
```

對照：官方認證的 Spring Boot `4.1.1` 組合對應下一條 Operaton 版本線——`operaton-root:2.2.0-M3`
宣告 `<version.spring-boot>4.1.1</version.spring-boot>`、`<version.spring>7.0.9</version.spring>`；
但 2.2.0 目前僅發佈到 `M3`（milestone），**尚無 GA**。

## 3. Spring Boot 4.0.8 與 4.1.1 BOM 對照（managed version 差異）

以兩份 `spring-boot-dependencies` POM 的 managed property 逐鍵比對，共 60 項變動。與本專案直接
使用之相依相關者：

| property | 4.0.8 | 4.1.1 |
|---|---|---|
| `spring-framework.version` | 7.0.9 | **7.0.9（不變）** |
| `spring-security.version` | 7.0.7 | 7.1.1 |
| `spring-session.version` | 4.0.5 | 4.1.1 |
| `micrometer.version` | 1.16.7 | 1.17.1 |
| `micrometer-tracing.version` | 1.6.7 | 1.7.1 |

其餘變動（Hibernate、Kafka、Jackson、各資料庫 driver、Maven plugins 等）本專案未直接使用，
不影響本 repo。`spring-session-hazelcast` 不由 Spring Boot 4 BOM 管理（此為既有事實，見 §5）。

## 4. 本機驗證證據

### 4.1 dependency:tree（實際解析，節錄）

```
org.springframework.boot:spring-boot:jar:4.1.1
org.springframework.boot:spring-boot-autoconfigure:jar:4.1.1
org.springframework:spring-core:jar:7.0.9
org.springframework:spring-context:jar:7.0.9
org.operaton.bpm:operaton-engine:jar:2.1.4
org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter:jar:2.1.4
org.springframework.session:spring-session-core:jar:4.1.1
org.springframework.session:spring-session-hazelcast:jar:4.0.0-M2   # pom 顯式 pin，BOM 不管理
```

### 4.2 全量測試（JDK 21，與 `.github/workflows/ci.yml` 相同）

```
mvn -B -ntp clean verify
...
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

逐類：`HazelcastPropertiesTest` 7、`SessionConfigurationTest` 7、`SessionIntegrationTest` 6、
`CamundaSessionE2ETest` 8、`HazelcastWorkflowIntegrationTest` 4（5 個既有測試類全綠、`Skipped: 0`）。

## 5. 殘餘風險與建議

1. **官方未認證**：Operaton 2.1.4 支援矩陣僅列 Spring Boot `4.0.8`；採納 `4.1.1` 屬官方所述
   「其他組合須充分測試」之情形。本 PR 已以既有全量測試提供實測證據，且 Spring Framework 維持
   `7.0.9` 不變，風險主要限於 spring-security `7.0.7 → 7.1.1` 與 spring-session `4.0.5 → 4.1.1`
   兩個 minor 升級。建議人類審查時明確接受此「未經 vendor 認證」狀態。
2. **`spring-session-hazelcast` 仍為 `4.0.0-M2`**：Spring Boot 4 對應之 Spring Session 將
   `spring-session-hazelcast` 移出 BOM，且其 GA 僅到 `3.5.7`、4.x 僅有 milestone。此為 Operaton
   遷移（Issue #27 報告 §4.3）時即已揭露並由人類接受的既有風險，本次升級未改變它。
3. **若要 vendor 認證組合**：須改升 Operaton 至 `2.2.0`（官方綁 Spring Boot `4.1.1`），但該版本
   目前僅 `M3`、無 GA；且升級 Operaton 屬本工作項範圍外（PRD 明示不含 Spring Boot 4.1.1 以外的
   依賴版本升級）。
4. **`spring-boot-maven-plugin`**：仍為 `4.0.8`；由 PR #34 另行升級。`clean verify` 不觸發其
   `repackage`/`build-image` goal，故不影響本 PR 的 CI 綠燈。

## 6. 驗證命令（可重現）

```bash
# 解析（確認 spring-boot 4.1.1、spring-framework 7.0.9、operaton 2.1.4）
mvn -B -ntp dependency:tree

# 全量測試（與 CI 相同）
mvn -B -ntp clean verify
```
