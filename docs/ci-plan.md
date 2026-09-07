# CI 導入方案報告（Issue #9）

> 任務類型：agent-analyze。**本報告為唯一產出**——未建立／未修改任何 `.github/**`、`pom.xml`、`src/**` 檔案；建議的 `ci.yml` 僅為文字草案（見 §6），落地由人類執行（factory-stop-rules SR3：CI 設定屬 guardrail 路徑，agent 不得代勞）。

## 1. 結論摘要（一頁）

1. **建置工具與版本事實**（自 `pom.xml` 與 `.mvn/wrapper/` 核實，詳 §2）：
   - Java 版本：**21**。注意：`pom.xml` 中**沒有** `<java.version>` 屬性；Java 版本由 `maven.compiler.source=21`／`maven.compiler.target=21`（pom.xml L13–14）宣告。
   - Spring Boot 版本：**3.4.4**（`spring-boot-dependencies` BOM import，pom.xml L21–26；`spring-boot-maven-plugin` 同版，L109–111）。專案**未繼承 `spring-boot-starter-parent`**，為自組 pom。
   - Packaging：**jar**（`pom.xml` 無 `<packaging>` 標籤，Maven 預設為 jar）。
   - Maven Wrapper：**存在**（`mvnw`／`mvnw.cmd`；wrapper 3.3.2，`distributionType=only-script`，固定下載 Apache Maven **3.9.10**，`.mvn/wrapper/maven-wrapper.properties` L17–19）。CI 可直接用 `./mvnw`。
2. **所有 5 個測試類（共 32 個測試）都可在 headless CI 直接執行，不需任何外部服務**：全部掛 `@ActiveProfiles("test")`，test profile 使用 **H2 記憶體資料庫**（`src/test/resources/application-test.yaml` L3）＋**內嵌單節點 Hazelcast**（`HazelcastAutoConfiguration` 以 `Hazelcast.newHazelcastInstance()` 於 JVM 內啟動）。已於 agent 環境以 JDK 21 實測：`mvn clean test` **BUILD SUCCESS，32/32 綠燈，無跳過**（實測紀錄 §3.3）。
3. **需外部服務（PostgreSQL／docker-compose／遠端 Hazelcast 叢集）的測試清單：空**。`docker-compose.yml`（postgres:17-alpine＋camunda＋webproxy）是**執行期**堆疊，與 `mvn test` 無交集。唯一名含 E2E 的 `CamundaSessionE2ETest` 也是自足測試（RANDOM_PORT 內嵌伺服器），實測綠燈。
4. **一項與打包相關的重要發現**：`mvn package` 產出的是**普通 class jar，不是可執行 fat jar**——實測 jar 內無 `BOOT-INF/`、MANIFEST 無 `Main-Class`。原因：未繼承 `spring-boot-starter-parent` 且 `spring-boot-maven-plugin` 未宣告 `<executions>` 綁定 `repackage` 目標（pom.xml L106–133）。CI 的 build/test/verify 不受影響，但 **CI 若要做 `java -jar` 啟動冒測試，需先另行修復此項**（建議工作項 §7-W2）。
5. **與既有 `.github/workflows/security.yml` 的關係**：無重疊、無需併入——該 workflow 僅在 `pull_request` 時做 checkout（全檔 17 行、無任何掃描／測試步驟，實際不具把關效力）；建議新增獨立的 `ci.yml`（功能面：建置＋測試閘門），兩者可並存（§6.3）。
6. **建議**：採方案 A（單 job：`setup-java` temurin 21 + Maven cache + `./mvnw -B -ntp clean verify`），完整草案 §6；落地與分支保護設定由人類執行。

## 2. 事實盤點（建置與測試組態）

### 2.1 `pom.xml` 關鍵事實（含行號）

| 項目 | 值 | 來源 |
|---|---|---|
| Java 版本 | 21（`maven.compiler.source`／`target`；**無 `<java.version>` 屬性**） | pom.xml L13–14 |
| Spring Boot | 3.4.4（BOM import；非 parent） | pom.xml L21–26 |
| Camunda BPM | 7.23.0（`camunda-bom` import） | pom.xml L29–34 |
| Hazelcast | 5.5.0（`hazelcast.version` 屬性） | pom.xml L15, L70–80 |
| PostgreSQL 驅動 | 42.7.7（**compile scope**，執行期用） | pom.xml L60–63 |
| H2 | test scope | pom.xml L98–102 |
| 測試框架 | `spring-boot-starter-test`（JUnit 5）test scope | pom.xml L92–96 |
| Packaging | 預設 `jar`（無 `<packaging>` 標籤） | pom.xml 全檔 |
| 構建外掛 | `spring-boot-maven-plugin` 3.4.4，僅 image env 設定、**無 executions** | pom.xml L106–133 |
| surefire/failsafe | **未做任何排除設定**（`mvn test` 會跑全部測試類，含 E2E） | grep 無命中 |

### 2.2 測試清單與外部依賴分析（共 32 個 `@Test`）

| 測試類 | 測試數 | SpringBootTest 型別 | 外部服務需求 | 判定 |
|---|---|---|---|---|
| `integration/CamundaSessionE2ETest`（L21–22） | 8 | `RANDOM_PORT`＋`ActiveProfiles("test")` | 無（內嵌伺服器＋內嵌 Hazelcast＋H2 mem；REST 打自己 `localhost:<隨機埠>`） | **可在 CI 跑**（實測綠） |
| `integration/HazelcastWorkflowIntegrationTest`（L13–14） | 4 | MOCK＋test profile | 無 | 可在 CI 跑 |
| `integration/SessionIntegrationTest`（L20–21） | 6 | MOCK＋test profile | 無（「多實例共用 session」為同 JVM 內模擬，非起第二個容器） | 可在 CI 跑 |
| `config/SessionConfigurationTest`（L17–19） | 7 | MOCK＋test profile | 無 | 可在 CI 跑 |
| `config/HazelcastPropertiesTest`（L10–11） | 7（純屬性測試） | MOCK＋test profile | 無 | 可在 CI 跑 |
| （對照）**需外部服務的測試** | **0** | — | — | **無此類測試** |

> 註：`@ActiveProfiles("test")` 使執行期設定（`src/main/resources/application.yaml` 指向 `jdbc:postgresql://postgresql:5432/camunda`，L2–6）**完全不進入測試路徑**；test profile 覆寫為 H2 記憶體並 `schema-update: true`（application-test.yaml L3–15）。`HazelcastAutoConfiguration.hazelcastInstance()`（L54–56）在 JVM 內起單節點；test profile 的 `instance-name` 帶 `${random.uuid}`（L21），避免併入任何現存叢集。

### 2.3 執行期堆疊（非測試依賴，僅供背景說明）

`docker-compose.yml`：`postgresql:17-alpine`＋`camunda`（image `philipz/camunda_hazelcast:7.23.0`）＋`webproxy`（nginx）。`mvn test` 不讀此檔；CI 的 build/test 階段**不需要 docker**。

## 3. 可在 headless CI 執行的完整命令＋agent 實測結果

### 3.1 命令（供 ci.yml 使用）

```bash
export JAVA_HOME=…(JDK 21)…
./mvnw -B -ntp clean verify            # 編譯＋全部 32 測試＋打包
# 或（系統 Maven 亦可，CI 建議用 wrapper 以固定 3.9.10）：
#   mvn  -B -ntp clean verify
./mvnw -B -ntp package -DskipTests     # 只要構件、跳測試
```

### 3.2 實測環境

- OS：Linux（CI 沙箱，無 GUI）；JDK：Temurin **21**（`/usr/lib/jvm/temurin-21-jdk-amd64`）；Maven：Apache 3.9.16（系統版）＋本機 repo `-Dmaven.repo.local=/tmp/m2repo`。
- 沙箱限制（**環境因素，非 repo 問題**）：`./mvnw` 在沙箱內失敗，原因為 sandbox 拒絕寫入 `~/.m2`（`mkdir: cannot create directory '/home/runner/.m2': Permission denied`，wrapper 需把 Maven 3.9.10 解壓至 `~/.m2/wrapper`），exit 1。GitHub Actions `ubuntu-latest` 無此限制，`./mvnw` 可用；本報告的實測改以系統 Maven 完成（版本 3.9.16，與 wrapper 釘選的 3.9.10 同屬 3.9.x，結果等值性高）。

### 3.3 實測結果摘要（REQ-3 證據）

`mvn -B -ntp clean test`（JDK 21）→ **BUILD SUCCESS**：

```text
[INFO] Compiling 7 source files with javac [debug target 21] to target/classes
[INFO] Compiling 5 source files with javac [debug target 21] to target/test-classes
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 21.16 s -- in com.example.workflow.integration.SessionIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.645 s -- in com.example.workflow.integration.CamundaSessionE2ETest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.039 s -- in com.example.workflow.integration.HazelcastWorkflowIntegrationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.492 s -- in com.example.workflow.config.SessionConfigurationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s -- in com.example.workflow.config.HazelcastPropertiesTest
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  02:35 min
```

`mvn -B -ntp package -DskipTests` → BUILD SUCCESS（增量快取約 4 秒），產出 `target/my-project-1.0.0-SNAPSHOT.jar`；`mvn -B -ntp clean verify` → **BUILD SUCCESS（Tests run: 32, Failures: 0, Errors: 0, Skipped: 0；Total time: 40.2 s，依賴暖快取）**。

**打包發現**：該 jar 為普通 class jar——`jar tf` 無 `BOOT-INF/`，`MANIFEST.MF` 僅 `Manifest-Version/Created-By/Java-Version: 21/Build-Jdk-Spec: 21`，**無 `Main-Class`**，`java -jar` 無法啟動。根因見 §2.1（plugin 未綁 `repackage` execution）。不影響 §6 草案（build＋test 閘門），但影響「CI 冒測試」可行性 → 列為後續工作項 §7-W2。

## 4. 影響範圍

- **受影響模組**：無——本報告不變更任何程式碼。落地 `ci.yml` 後影響面為所有 PR 與 trunk push（目前 trunk 為 `software-factory`，最終 `main`）。
- **開發者／審查者**：CI 綠燈將成為 PR 把關訊號；預估單次執行 ~3–5 分鐘（本機 2:35 不含冷啟動依賴下載；GitHub 快取後首次約 5–8 分鐘）。
- **下游**：`philipz/camunda_hazelcast:7.23.0` 映像是手動建立（compose 引用）；`spring-boot:build-image` 外掛設定存在（pom.xml L112–130，GC/heap dump 環境變數），本報告不建議在初版 CI 觸發映像建置（需 Docker registry 憑證，屬第二階段）。
- **既有 workflow**：`security.yml` 與 `permissions: pull-requests: write` 不受 `ci.yml` 影響（不同檔案、不同職責）。

## 5. 方案比較

| 方案 | 內容 | 評價 |
|---|---|---|
| **A（建議）** | 單 job：`checkout` → `setup-java@v4`（temurin 21＋`cache: maven`）→ `./mvnw -B -ntp clean verify`；`push`（main／software-factory）＋`pull_request` 雙觸發；`timeout-minutes: 15`；`concurrency` 取消過時 run | 完整覆蓋現有 32 測試（實測全綠）；無需 docker／services；維護成本最低；草案 §6 |
| B | 在 A 上加 `services:`（postgres 容器）跑整合測試 | **拒絕**：目前無任何測試使用外部 PG（§2.2），加 services 徒增啟動時間與 flake；未來若新增真整合測試再啟用（草案 §6 內保留註解範例） |
| C | JDK matrix（21＋x） | **拒絕**：pom 釘死 target 21 且無多版本承諾；單一版本符合現況，待有相容性需求再展開 |
| D | 把建置測試塞進既有 `security.yml` | **拒絕**：職責不同（閘門 vs 審查輔助）、permissions 不同（`pull-requests: write` 僅 security 需要）；混在一起會讓後續 branch protection 設定（required check）綁上非必要權限的 workflow |

## 6. 建議的 `ci.yml` 完整草案（僅文字，**不得由 agent 寫入 `.github/**`**）

### 6.1 草案

檔案位置（由人類建立）：`.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, software-factory]
  pull_request:

permissions:
  contents: read

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-test:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'   # 與 pom.xml maven.compiler.source/target=21 一致（§2.1）
          cache: maven

      # 使用 Maven Wrapper（釘選 Maven 3.9.10，.mvn/wrapper/maven-wrapper.properties）
      - name: Build and test
        run: ./mvnw -B -ntp clean verify

      - name: Upload jar artifact (on trunk only)
        if: github.event_name == 'push'
        uses: actions/upload-artifact@v4
        with:
          name: my-project-jar
          path: target/*.jar

      # （選用）未來若有真需 PostgreSQL 的整合測試，再啟用 services：
      # services:
      #   postgresql:
      #     image: postgres:17-alpine
      #     env: { POSTGRES_DB: camunda, POSTGRES_PASSWORD: password }
      #     ports: ['5432:5432']
      #     options: >-
      #       --health-cmd "pg_isready -U postgres" --health-interval 5s
      #       --health-timeout 5s --health-retries 5
```

### 6.2 一致性檢查

- 草案 JDK `21` ＝ §2.1 自 `pom.xml` 讀出的編譯目標（一致，REQ-4）。
- 命令與 §3 實測命令一致（僅 Maven 來源不同：CI 用 wrapper 3.9.10，agent 實測系統 3.9.16，同屬 3.9.x）。
- 註：`verify` 因專案無 failsafe IT，實際等價 `test`＋`package`，兩者皆已實測綠燈（§3.3）。

### 6.3 與既有 `security.yml` 的關係（共存）

| | `security.yml`（現存） | `ci.yml`（本草案） |
|---|---|---|
| 觸發 | `pull_request` | `push`（main／software-factory）＋`pull_request` |
| 內容 | 僅 checkout（17 行，無掃描步驟，**目前不產生任何檢查結果**） | build＋32 測試＋打包 |
| permissions | `pull-requests: write`（留言用） | `contents: read`（最小權限） |
| 把關 | 否 | 可設為 required check（branch protection 屬人類操作） |

**結論：不重疊、不互斥、無需併入**；兩檔可同目錄並存。若後續 `security.yml` 補上掃描步驟，建議保持獨立 workflow，僅在 branch protection 同時列為 required。

## 7. 建議下一步（可直接開成工作項）

- **W1（人類執行，非 agent 工作項）**：將 §6.1 草案貼入 `.github/workflows/ci.yml`、於 trunk 上建立並觀察一次綠燈 run，再把 `build-test` 設為 branch protection required check。
  驗收條件草案：(a) `ci.yml` 存在且首 run 綠燈；(b) 開一個noop PR 可見 required check 生效；(c) 未觸碰 `security.yml`。
- **W2（agent-fix-bug）**：修復 fat jar 打包——在 `spring-boot-maven-plugin` 加 `repackage` execution（或改繼承 `spring-boot-starter-parent`）。
  驗收條件草案：(a) `jar tf target/*.jar` 含 `BOOT-INF/lib/`；(b) MANIFEST 有 `Main-Class: org.springframework.boot.loader.launch.JarLauncher` 與 `Start-Class: com.example.workflow.Application`；(c) 既有 32 測試全綠。
- **W3（agent-add-tests，選作）**：CI 冒測試——依賴 W2；用 `test` profile 以 `java -jar` 起應用並打 `/actuator/health`。
  驗收條件草案：冒測試於 CI 綠燈、總時程增量 <60s。
- **W4（小型維護，agent）**：test profile 加固項目——(a) `application-test.yaml` 的 `management.server.port: 9000` 改為 `0`（隨機埠），消除未來多 `RANDOM_PORT` 測試類並存時的埠衝突；(b) 評估於 test 設定顯式關閉 Hazelcast multicast join（現行 `${random.uuid}` 實例名已足夠安全，屬防禦性加深）。
  驗收條件草案：32 測試仍全綠；兩個 `RANDOM_PORT` 測試類並存不衝突（可加一個佔位測試驗證）。

## 8. 驗收條件自查（REQ 對照）

| REQ | 條件 | 涵蓋 |
|---|---|---|
| REQ-1 | `docs/ci-plan.md` 存在且為唯一新增檔案；diff 不含 `.github/` | ✅ 本檔即唯一產出（見 PR changed files） |
| REQ-2 | 載明自 pom.xml 讀出的 Java／Spring Boot 版本且與檔案一致 | ✅ §1.1／§2.1：Java 21（如實註明：pom 無 `<java.version>`，係 `maven.compiler.source/target=21`）、Spring Boot 3.4.4、packaging 預設 jar |
| REQ-3 | 建置命令於 agent 環境實執行並貼結果（或明列失敗原因與前置） | ✅ §3.2–3.3（含 `./mvnw` 於沙箱失敗之具體原因與 CI 可用性判斷） |
| REQ-4 | 含完整 `ci.yml` 草案（fenced block）且 JDK 版本一致 | ✅ §6.1–6.2 |
| REQ-5 | 列出需外部服務的測試與 CI 處置 | ✅ §2.2（為空集合）＋§5 方案 B（保留未來 services 範本） |
