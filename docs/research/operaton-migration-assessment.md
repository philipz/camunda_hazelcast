# Camunda 7.23 → Operaton 2.1.4（Spring Boot 4.0.8）遷移可行性評估

- Issue: #27
- 分析日期：2026-09（依 repo 內容與 Maven Central / Operaton 官方文件查證）
- 分析範圍：唯讀。本報告為 `docs/research/` 下的唯一新增檔案，未修改任何既有檔案。

## 1. 結論摘要

1. **遷移在技術上可行**：Operaton 2.1.4 的全部相依 artifact（BOM、engine、spring-boot-starter-rest/webapp、
   engine-plugin-spin、spin-dataformat-all）在 Maven Central 均**已查證存在**（見 §3）。Operaton 官方文件
   明確聲明 2.1.4 對應 `spring-boot = 4.0.8`、`spring = 7.0.9`、`Java 17+`，與本專案目標（Spring Boot 4）一致。
2. **但目前不應立即動手實作**：唯一的硬性阻礙是 **`spring-session-hazelcast` 尚無支援 Spring Boot 4 的 GA
   版本**——Maven Central 上最新 GA 僅到 `3.5.7`（對應 Spring Boot 3.x 系列），Spring Boot 4 對應版本目前只有
   `4.0.0-M1`／`4.0.0-M2` 兩個 milestone（見 §4）。這是本專案的分散式 session（`spring-session-hazelcast`，
   `pom.xml:78-81`）能否在 Spring Boot 4 下正常運作的關鍵未知數。
3. **必然觸及 H3（`.github/factory/risk-paths.yml:24-27`）**：`application.yaml`／`application-dev.yaml` 內嵌
   PostgreSQL 與 Camunda admin 明文密碼，遷移必然編輯這兩個檔案（設定 key 改名），H3 保護範圍精準命中。
4. **OpenRewrite recipe（`migrate-camunda-recipe:1.0.1`）能自動處理依賴座標、Java 套件/型別/常數改名、BPMN
   XML namespace**，但**不處理 `application*.yaml` 的設定 key 改名**（`camunda.bpm.*` → `operaton.bpm.*`）。
   這部分必須人工執行，且本 repo 只有 3 個檔案含此 key，工作量小但不可省略（見 §5）。
5. **建議下一步（見 §7）**：先由人類裁定 spring-session-hazelcast 的方案（A/B/C，見 §4.3），裁定後才可開立
   agent-fix-bug／agent-add-tests 型工作項執行實際遷移；且遷移前必須先把 `software-factory` trunk 與 `main`
   的 `camunda-bom` 版本差異（見 §2.4）對齊到 7.24.0，否則不符合 Operaton 官方遷移前提（見 §2.4、§6）。

## 2. 現況盤點

### 2.1 Java 原始碼中的 Camunda 引用

| 檔案 | 內容 |
|---|---|
| `src/main/java/com/example/workflow/tasks/putServiceDelegate.java:5-7` | `import org.camunda.bpm.engine.delegate.JavaDelegate/DelegateExecution/BpmnError` |
| `src/main/java/com/example/workflow/tasks/getServiceDelegate.java:5-7` | 同上三個 import |
| `src/main/java/com/example/workflow/config/HazelcastProperties.java:10` | `instanceName = "camunda-hazelcast"`（字串常數，非 API 引用，遷移非必要但語意上可考慮更名） |
| `src/main/java/com/example/workflow/Application.java:30` | 純日誌字串 `"=== Camunda Hazelcast Integration Status ==="`（非程式相依） |

共 **2 個檔案**（`putServiceDelegate.java`、`getServiceDelegate.java`）直接 import `org.camunda.bpm.engine.delegate.*` API，是 OpenRewrite `ChangePackage` recipe 的目標。

### 2.2 BPMN 檔案中的 camunda 命名空間與屬性

`src/main/resources/process.bpmn:2-17`：
- Line 2：`xmlns:camunda="http://camunda.org/schema/1.0/bpmn"`（BPMN 擴充 namespace，非 §2 mapping 表列出的
  `BpmPlatform`/`ProcessApplication` namespace，但官方遷移指南指出**引擎同時接受 `operaton:` 與舊
  `camunda:` 擴充 namespace**，故此檔案理論上**不強制修改**即可繼續運作，僅建議透過 recipe 統一改名以避免
  混用造成日後維護困惑）。
- Line 3：`camunda:historyTimeToLive="0"`
- Line 12：`camunda:delegateExpression="#{putServiceDelegate}"`（serviceTask `rest-api`）
- Line 17：`camunda:delegateExpression="#{getServiceDelegate}"`（serviceTask `Activity_0svc0q6`）

`src/main/resources/getprocess.bpmn` 存在但未列在 Issue 盤點清單中，經檢查其結構與 `process.bpmn` 相同（同樣使用 `camunda:` namespace 與 `delegateExpression`），遷移時應一併處理。

### 2.3 `application*.yaml` 中的 `camunda.bpm.*` 設定 key

| 檔案 | 行號 | 內容 |
|---|---|---|
| `src/main/resources/application.yaml` | 24-26 | `camunda.bpm.admin-user: {id: demo, password: demo}` |
| `src/main/resources/application-dev.yaml` | 8-10 | 同上 |
| `src/test/resources/application-test.yaml` | 8-16 | `camunda.bpm.admin-user`（id/password）+ `camunda.bpm.database`（schema-update/type）+ `camunda.bpm.job-execution.enabled` |

Operaton 官方文件（[Configure a Spring Boot Project](https://docs.operaton.org/docs/get-started/spring-boot/configuration/)）
明確示範設定 key 為 **`operaton.bpm.*`**（非 `camunda.bpm.*`）。3 個檔案、共 3 組 key 區塊需要人工改名
（recipe 不涵蓋，見 §5）。

### 2.4 pom.xml 相依（及 trunk 分支落差警示）

`pom.xml:19-31` 兩個受影響的 `<dependencyManagement>` import：
```xml
<dependency> <groupId>org.springframework.boot</groupId> <artifactId>spring-boot-dependencies</artifactId> <version>3.4.4</version> ... </dependency>
<dependency> <groupId>org.camunda.bpm</groupId> <artifactId>camunda-bom</artifactId> <version>7.23.0</version> ... </dependency>
```
`pom.xml:36-58` 四個直接相依 Camunda artifact：
`camunda-bpm-spring-boot-starter-rest`、`camunda-bpm-spring-boot-starter-webapp`、
`camunda-engine-plugin-spin`、`camunda-spin-dataformat-all`（皆無顯式 `<version>`，由 `camunda-bom` 管理）。

**⚠️ trunk 分支版本落差**：`gh pr view 21` 顯示「bump camunda-bom 7.23.0 → 7.24.0」已於 `main` 分支合併，
但工廠 trunk `software-factory` 分支的 `pom.xml` 仍是 `7.23.0`（本次分析當下核對，`git log software-factory`
只有 2 個 commit，未含該 bump）。Operaton 官方遷移指南的**前提條件**是「先升級到 Camunda 7.24，再切換到
Operaton」（因為 schema/API 相容性是對齊 7.24 而非 7.23）。這代表：
- 若遷移工作項基於 `software-factory` trunk 開始，**必須先確認/執行 camunda-bom → 7.24.0 這一步**
  （可能需要先把 `main` 與 `software-factory` 的分岔同步，或在遷移 PR 中一併升級）。
- 這不是「遷移本身」的問題，而是**前置依賴条件**，建議在下一步工作項描述中明確列出（見 §7）。

`pom.xml:120` build 段的 `spring-boot-maven-plugin` 版本 `3.4.4` 亦需隨 Spring Boot 4 同步升級。

### 2.5 其他觸及點（Issue 未列但盤點發現）

- `docker-compose.yml:9,22-23,31`：`POSTGRES_DB=camunda`（僅為 DB 名稱字串，非程式相依，不受影響）；
  `camunda: image: philipz/camunda_hazelcast:7.23.0`（自建映像 tag，遷移後語意上應改名，但屬 CI/CD 範疇，
  超出本次程式碼遷移範圍）。
- `src/test/java` 下 5 個測試類中 3 個含 `camunda`/`Camunda` 字樣（`CamundaSessionE2ETest.java`、
  `HazelcastWorkflowIntegrationTest.java`、`HazelcastPropertiesTest.java`），主要是類別命名與 API 呼叫，
  詳見 §3.3 測試影響分析。

## 3. 相依對應表（Camunda → Operaton，2.1.4 已查證存在）

以下為本 repo 實際使用的 4 個 Camunda artifact 的對應表，均已透過
`https://repo1.maven.org/maven2/.../maven-metadata.xml` 逐一查證 **2.1.4 版本確實存在**於 Maven Central：

| Camunda GroupId:ArtifactId | Operaton GroupId:ArtifactId | 2.1.4 是否存在 |
|---|---|---|
| `org.camunda.bpm:camunda-bom` | `org.operaton.bpm:operaton-bom` | ✅（已 fetch maven-metadata.xml 確認） |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter-rest` | ✅ |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter-webapp` | ✅ |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `org.operaton.bpm:operaton-engine-plugin-spin` | ✅ |
| `org.camunda.spin:camunda-spin-dataformat-all` | `org.operaton.spin:operaton-spin-dataformat-all` | ✅ |

對應表出處：[Camunda to Operaton Artifact Mapping](https://github.com/operaton/migrate-from-camunda-recipe/blob/main/camunda-to-operaton-mapping.md)（官方 recipe repo）。

**關鍵版本事實**（來自 `operaton-root:2.1.4` 的 POM，
`https://repo1.maven.org/maven2/org/operaton/bpm/operaton-root/2.1.4/operaton-root-2.1.4.pom`）：
```xml
<version.java>17</version.java>
<version.spring>7.0.9</version.spring>
<version.spring-boot>4.0.8</version.spring-boot>
```
與 Issue 描述的「Spring Boot 4.0.8」完全吻合。

**Java package 改名範圍**（recipe `ChangePackage`，只影響以下 4 個 root package，遞迴改名）：
`org.camunda.bpm → org.operaton.bpm`、`org.camunda.spin → org.operaton.spin`、
`org.camunda.commons → org.operaton.commons`、`org.camunda.connect → org.operaton.connect`。
本 repo 兩個 delegate 檔案的 import 剛好落在 `org.camunda.bpm` 範圍內，可被 recipe 自動處理。

## 4. spring-session-hazelcast 方案比較（本次分析重點）

### 4.1 現況與查證結果

- `pom.xml:78-81` 的 `org.springframework.session:spring-session-hazelcast` **無顯式 `<version>`**，由
  Spring Boot BOM 的 `dependencyManagement` 管理版本。
- 查證 Maven Central metadata（`https://repo1.maven.org/maven2/org/springframework/session/spring-session-hazelcast/maven-metadata.xml`）：
  最新版本列表顯示 **GA 系列止於 `3.5.7`**（對應 Spring Session 3.x / Spring Boot 3.x），
  Spring Boot 4 對應的 Spring Session 4.x 系列**只發佈了 `4.0.0-M1` 與 `4.0.0-M2` 兩個 milestone**，
  **尚無 GA 版本**。
- `spring-boot-dependencies:4.1.1`（PR #18 嘗試升級的目標版本）的 release note 提到
  「Upgrade to Spring Session **4.1.1**」——但這是指 `spring-session-core`；`spring-session-hazelcast` 是
  獨立 artifact，**未必**與 `spring-session-core` 同步發版（前述 metadata 已證實其最新僅到 4.0.0-M2）。
  Operaton BOM（`operaton-only-bom`）亦不管理 `spring-session-hazelcast`（Operaton 專注 BPM engine 相依，
  非通用 Spring Session 生態）。
- 結論：**Spring Boot 4 + `spring-session-hazelcast` 的組合目前沒有官方 GA 版本可用**，這是 Issue 描述
  的核心痛點已被證實成立。

### 4.2 對既有 5 個測試類的影響面

`src/test/java` 下的 5 個測試類：
1. `config/HazelcastPropertiesTest.java` — 純 POJO 屬性測試，不依賴 session，**不受影響**。
2. `config/SessionConfigurationTest.java` — 測試 `SessionConfig` bean 是否正確組態
   （`spring-session-hazelcast` 直接相關），**高度受影響**。
3. `integration/SessionIntegrationTest.java` — Session 儲存/讀取整合測試，**高度受影響**。
4. `integration/CamundaSessionE2ETest.java` — Camunda 登入 + session 分散式驗證的 E2E 測試，
   **同時受 Camunda→Operaton 與 session 方案雙重影響**，風險最高。
5. `integration/HazelcastWorkflowIntegrationTest.java` — BPMN 流程 + Hazelcast 資料存取整合測試，
   **主要受 Camunda→Operaton 影響，受 session 方案影響較小**（該測試聚焦 workflow data，非 HTTP session）。

### 4.3 三方案比較

**方案 A：使用 `spring-session-hazelcast:4.0.0-M2`（milestone）**

| 面向 | 說明 |
|---|---|
| 可行性 | 技術上可直接在 `pom.xml` 指定該版本並保留其餘 Spring Boot 4 GA 相依。需額外加入 Spring milestone
  repository（`https://repo.spring.io/milestone`），因 milestone 版本通常不在 Maven Central 正式索引外
  （**待人類確認**：本查證僅透過 `repo1.maven.org` 確認 metadata 中列出此版本號，未逐一確認 jar 本身是否
  發佈在 Central 還是僅在 Spring milestone repo；若後者則需修改 `pom.xml` 加入額外 repository，屬於「新增
  未在既有相依清單中的套件來源」，依 factory-stop-rules 第 5 條應停手交人類裁定）。 |
| 對 5 個測試類影響 | 若 API 相容，2-4 項測試可望維持綠燈；但 milestone 版本可能存在未公告的 breaking change
  或未修復的 bug，需完整跑一次測試套件才能確認。 |
| 風險 | **生產環境使用非 GA 版本**——milestone 版本無 SLA、無安全更新保證，且可能在正式 GA 版本發佈時出現
  API 或行為差異，需要二次遷移。此風險與 CLAUDE.md 中「Production Configuration」章節強調的
  session 安全性（cookie secure、same-site strict）目標相悖。 |
| 回滾難度 | 中等——只需改回 GA 版本號，但若 milestone 版本間有資料格式差異，正式環境的既有 session 資料
  可能需要清空重建（可接受，因 session 本為短生命週期資料）。 |

**方案 B：暫時移除分散式 session（改用預設記憶體內 session 或改移除 Spring Session 相依）**

| 面向 | 說明 |
|---|---|
| 可行性 | 移除 `pom.xml:78-81` 的相依後，Spring Boot 預設退回 servlet 容器內建 session（記憶體內，
  非分散式）。技術上直接可行，`SessionConfig.java` 需同步調整或移除。 |
| 對 5 個測試類影響 | `SessionConfigurationTest.java`、`SessionIntegrationTest.java` 兩者**斷言的正是分散式
  session 行為**（依 CLAUDE.md「Spring Session with Hazelcast」章節描述的水平擴展、重啟後 session
  保留等特性），移除後這兩個測試的既有斷言**必然失敗**，若要保留現有斷言則此方案不可行；若同意弱化/
  刪除相關斷言則違反 factory-stop-rules「不得為了讓測試通過而刪除或弱化測試斷言」。 |
| 風險 | **架構回退**——CLAUDE.md 明確記載本專案的設計目標是「Horizontal scaling」「Session persistence
  across application restarts」「Shared session state in clustered deployments」，移除分散式 session
  等同放棄此核心非功能需求，屬於範圍大幅變更，需人類明確同意。 |
| 回滾難度 | 低（技術上容易加回），但業務影響需人類評估（若正式環境已依賴多實例部署，移除將直接破壞水平
  擴展能力）。 |

**方案 C：延後遷移，等待 `spring-session-hazelcast` GA 支援 Spring Boot 4**

| 面向 | 說明 |
|---|---|
| 可行性 | 最保守方案。持續使用 Camunda 7.24 / Spring Boot 3.x，定期追蹤
  `spring-session-hazelcast` Maven Central release，待 GA 後再啟動遷移。 |
| 對 5 個測試類影響 | **零影響**——不改動任何程式碼，測試維持現狀綠燈。 |
| 風險 | 低（維持現状），但持續錯失 Spring Boot 4 的功能與安全更新（Spring Boot 3.x 終將進入 EOL，
  需另外追蹤 Spring Boot 3.x 支援週期以評估「延後」的時間上限）。 |
| 回滾難度 | 無需回滾（未變更）。 |

**被拒方案：直接升 Spring Boot 4 但保留 Camunda 7（不遷移到 Operaton）**——已由 PR #18（CI 失敗）
證實不可行，根因是 Camunda 7.23.0 相依 Spring Framework 6 / Jakarta EE 10，與 Spring Boot 4（Spring
Framework 7 / Jakarta EE 11）的 API 不相容，非本報告新增選項，此處僅重申拒絕理由以完整呈現決策脈絡。

**本報告不代為裁定 A/B/C**：三方案各自的取捨（milestone 風險 vs. 架構回退 vs. 延後時程）涉及產品/架構
層級判斷，依 Issue 描述「在動手改程式碼前，需要先把方案選擇攤開供人類裁定」的要求，**裁定留給人類**。

## 5. 風險評估

### 5.1 H3（敏感資料處理）—— 確認必然觸及

`.github/factory/risk-paths.yml:24-27` 明確列出 `src/main/resources/application*.yaml` 為 H3 命中路徑
（內嵌 DB 與 admin 憑證）。遷移方案無論選 A/B/C，只要涉及 Camunda→Operaton 的設定 key 改名
（`camunda.bpm.*` → `operaton.bpm.*`，§2.3），就必然編輯 `application.yaml`、`application-dev.yaml`
（以及 `src/test/resources/application-test.yaml`，該檔不在 `application*.yaml` glob 但同樣含
`camunda.bpm.*`，屬性質相同的敏感/設定資料，建議一併纳入 H3 同等審慎程度處理）。
**任何後續實作工作項觸及此範圍時，依現行 risk-paths.yml 規則 risk = 2，無裁量空間**，需要對應的人類審查
流程（H3 為硬規則，非本報告可豁免）。

### 5.2 `.github/ci.yml` 是否需要改動

`.github/workflows/ci.yml:16-20` 目前設定 `java-version: '21'`。Operaton `operaton-root:2.1.4` 的
`<requireJavaVersion><version>[17,)</version>` 要求 **JDK 17 以上**，本 repo 的 JDK 21 **已滿足**此要求，
**CI 的 JDK 版本設定不需改動**。

但 `.github/ci.yml` 屬於 H5（guardrail 自身保護路徑，`risk-paths.yml:30-34`），若遷移過程需要調整 Maven
指令（例如加入 `rewrite:run` 步驟）則會觸及 H5，依規則「agent 不得修改約束自己的規則」——**建議遷移工作項
不要求修改 `.github/ci.yml` 本身**（OpenRewrite recipe 可在本地／獨立 PR 準備分支時執行一次性轉換，
不需要成為 CI 的常態步驟）。

### 5.3 pom.xml 版本管理（非 H 類風險，但屬遷移工作量）

- `spring-boot-maven-plugin`（`pom.xml:120`）需與 `spring-boot-dependencies` 同步由 `3.4.4` 升至對應
  Spring Boot 4 版本（Operaton 使用 `4.0.8`）。
- `org.postgresql:postgresql`（`pom.xml:60-64`，目前 `42.7.7`，注意 trunk 落後 `main` 的 `42.7.13`，
  另有 dependabot PR #20 已合併至 main）與 `com.hazelcast:*`（`hazelcast.version=5.5.0`，同樣落後 `main`
  的 `5.7.0`，dependabot PR #16 已合併至 main）需個別確認與 Spring Boot 4 / Operaton 2.1.4 的相容性
  （本報告未逐一查證，因其屬既有相依而非本次評估的核心相依，建議列入下一步工作項的驗證清單）。

## 6. OpenRewrite recipe（`org.operaton:migrate-camunda-recipe:1.0.1`）涵蓋範圍評估

依官方 recipe README（[operaton/migrate-from-camunda-recipe](https://github.com/operaton/migrate-from-camunda-recipe)），
`MigrateSpringBootApplication` meta recipe 包含以下 8 個子 recipe：

| 子 recipe | 能否自動處理 | 本 repo 對應範圍 |
|---|---|---|
| `ReplaceCamundaDependencies` | ✅ 自動 | `pom.xml` 的 4 個 Camunda artifact 座標改名（§3） |
| `ChangeMethod` | ✅ 自動（若命中） | 本 repo 兩個 delegate 檔案未見含 `Camunda` 字樣的方法呼叫，預期無變更點 |
| `ChangeConstant` | ✅ 自動（若命中） | 同上，未見相關常數使用 |
| `ChangeType` | ✅ 自動（若命中） | 本 repo 未直接使用 mapping 表列出的 `CamundaXxx` 型別（如 `CamundaFormRef`），預期無變更點 |
| `ChangePackage` | ✅ 自動 | `putServiceDelegate.java`、`getServiceDelegate.java` 的 `org.camunda.bpm.*` import（§2.1） |
| `MigrateDeploymentDescriptors` | ✅ 自動 | `process.bpmn`、`getprocess.bpmn` 的 XML namespace 屬性（若選擇改名，見 §2.2 附註） |
| `RenameServiceLoader` | ✅ 自動（若命中） | 需檢查 `camunda-spin-dataformat-all` 是否在本 repo 的 build 產物中含
  `META-INF/services/org.camunda.spin.spi.DataFormatProvider`（recipe 官方 Known Issue 特別點名此案例，
  **檔案名會被改但內容不會**，需人工複查，見 recipe README「Known Issues」） |
| `ResolveDeprecations` | ✅ 自動（若命中） | 本次未逐一比對 deprecated API 清單，建議實作時觀察 recipe 執行輸出 |

**recipe 明確不涵蓋的部分**（官方文件與 README 均未提及 YAML/properties 檔案）：
- **`application*.yaml` 的 `camunda.bpm.*` → `operaton.bpm.*` 設定 key 改名**（§2.3 三個檔案、共 3 組
  key 區塊）——**必須人工執行**。這是 Issue 第 5 點提問「特別是 application*.yaml 的設定 key 改名是否在
  recipe 涵蓋範圍內」的直接答案：**不在**。
- Docker image tag（`docker-compose.yml:23`）、GC 監控文件（`gc-monitoring.yaml`、`hazelcast_gc.md`）中
  若提及 camunda 字樣，同樣不在 recipe 範圍，需人工檢視（本次盤點未見這兩檔含 camunda 相依性字樣，
  僅供下一步工作項參考）。

## 7. 建議下一步

依 Issue 要求「給出可直接開成工作項的具體步驟與建議的執行順序」，建議依序拆分為以下工作項（**均需人類
先行裁定 §4.3 的 session 方案後才可執行**）：

1. **【人類裁定】spring-session-hazelcast 方案選擇（A/B/C）**——本報告已列出三方案的可行性、測試影響、
   風險、回滾難度（§4.3），無法由 agent 自動裁定，需先產出此決策才能繼續。

2. **agent-update-deps：`software-factory` trunk 同步 camunda-bom 7.23.0 → 7.24.0**（前置步驟，§2.4）
   - 驗收條件草案：
     - [ ] `pom.xml` 的 `camunda-bom` 版本更新為 `7.24.0`
     - [ ] `mvn clean verify` 全量測試綠燈（5 個既有測試類）
     - [ ] 確認與 `main` 分支的 `camunda-bom` 版本一致（消除分岔）

3. **agent-fix-bug 或專屬遷移工作項：執行 OpenRewrite `MigrateSpringBootApplication` recipe**
   （依裁定的 session 方案，§4.3 決定後才啟動）
   - 驗收條件草案：
     - [ ] `pom.xml` 相依改為 `org.operaton.bpm:operaton-bom:2.1.4` 及對應 4 個 operaton artifact
       （§3 對應表）
     - [ ] `spring-boot-dependencies` 升至 `4.0.8`（對齊 Operaton 2.1.4 使用的版本）
     - [ ] `spring-boot-maven-plugin` 版本同步更新
     - [ ] `putServiceDelegate.java`、`getServiceDelegate.java` 的 import 改為 `org.operaton.bpm.engine.delegate.*`
     - [ ] `process.bpmn`、`getprocess.bpmn` 的 `camunda:` namespace／屬性依 recipe 輸出結果處理（或確認
       保留 `camunda:` 亦可運作，依官方「同時接受兩種 namespace」的說明二選一，並在 PR 描述中明確記錄）
     - [ ] `application.yaml`、`application-dev.yaml`、`application-test.yaml` 的 `camunda.bpm.*` 改為
       `operaton.bpm.*`（人工執行，recipe 不涵蓋，§6）
     - [ ] 依裁定方案調整 `spring-session-hazelcast`（版本號或移除，§4.3）
     - [ ] 檢查 recipe 執行輸出中的 `RenameServiceLoader` 警告並人工複查（§6）
     - [ ] `mvn clean verify` 全量測試綠燈，5 個既有測試類逐一確認（§4.2 影響分析）
     - [ ] `docker-compose.yml` 的 `camunda:` image tag 語意更新（可選，屬 CI/CD 範疇，非阻塞項）
   - 此工作項**必然觸及 H3**（`application*.yaml`），需依現行 risk-paths.yml 走對應的人類審查流程。

4. **agent-write-docs：更新 `CLAUDE.md`**——遷移完成後，`CLAUDE.md` 中提及 "Camunda BPM 7.23.0"、
   "camunda-bpm-spring-boot-starter-*" 等技術棧描述需同步更新為 Operaton 對應內容。

**執行順序**：1（人類裁定）→ 2（trunk 同步，可與 1 平行）→ 3（實際遷移，依 1 的裁定結果調整範圍）→ 4（文件）。
