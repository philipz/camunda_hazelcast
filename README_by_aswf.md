# 納管提案：philipz/camunda_hazelcast（Issue #5）

本目錄為 factory agent 對 `philipz/camunda_hazelcast` 的**唯讀分析產出**，供人類審核裁定。
三軸的值一律以 `TODO` 佔位——人類裁定後，親自執行下方搬檔指令才生效。

## 一、掃描摘要

| 項目 | 結果 |
|---|---|
| 語言 | Java 21 |
| 建置工具 | Maven（單一模組 `pom.xml`，無 `<modules>`） |
| 框架 | Spring Boot 3.4.4 + Camunda BPM 7.23.0 |
| 快取/會話 | Hazelcast 5.5.0 + Spring Session（distributed session） |
| 測試框架 | JUnit（`spring-boot-starter-test`；實際測試用 `@Test`） |
| 資料庫 | 生產 PostgreSQL 42.7.7；測試 H2（`src/test/resources/application-test.yaml`） |
| 原始檔 | `src/main/java/com/example/workflow/` 共 7 檔（Application + config×4 + tasks×2） |
| 測試檔 | `src/test/` 共 5 檔 + application-test.yaml |
| 部署 | `docker-compose.yml`：postgresql / camunda / webproxy（nginx 反代）三容器 |

**這個 repo 做什麼**：示範 Camunda BPM 如何整合 Hazelcast 作為分散快取，存放工作流程
變數與 Spring Session 分散會話。`README.md`／`CLAUDE.md` 明言為 integration demo；
`pom.xml` 的 `artifactId=my-project`、`groupId=com.example.workflow`、`1.0.0-SNAPSHOT`
皆為示範定位。兩個 service delegate（`getServiceDelegate`/`putServiceDelegate`）僅做
Hazelcast map 的 put/get。

**關鍵發現**：
- **無認證/授權邏輯程式碼**：`pom.xml` 無 `spring-boot-starter-security`；全 repo 無
  `@PreAuthorize`、`UserDetailsService`、authentication provider。但 **Camunda webapp/REST
  由 starter 提供**，內建 Camunda 自身的帳號機制。
- **無金流、無加密、無 PII、無 migrations/、無 openapi/proto**：全部掃描無命中。
- **唯一的敏感資料面＝設定檔內嵌憑證**：`src/main/resources/application.yaml` 與
  `application-dev.yaml` 硬編碼 `postgres password: "password"`、`camunda.bpm.admin-user
  demo/demo`；`docker-compose.yml` 亦硬編碼 `POSTGRES_PASSWORD=password` 且
  `POSTGRES_HOST_AUTH_METHOD=trust`。
- **單一服務**：runtime 邏輯全在一個 Spring Boot app；docker-compose 的三容器只是部署
  拓樸，跨服務協調僅「Camunda → Hazelcast/Postgres」本機相依，無事件/訊息佇列耦合。

## 二、三軸建議值與理由（完整理由詳見 catalog-info.yaml 註解）

| 軸 | 建議值 | 一句理由 |
|---|---|---|
| business-criticality | `supporting`（0） | 示範/教學 repo，非對外客戶系統、非工廠基礎設施；停機不影響任何 SLA。 |
| risk-profile | `medium`（1） | 設定檔硬編碼 DB/admin 憑證（敏感資料外洩面，同 factory-scoreboard 匿名可讀型風險）；但無 auth 邏輯、無金流/PII/加密，不到 high。 |
| complexity | `low`（0） | 7 個原始檔、單一模組、無 migrations/openapi/proto、單一服務；delegate 只做 put/get。 |

總分建議＝ 0 + 1 + 0 ＝ **1 → on-loop**（未觸發硬規則時）。

## 三、搬檔指令（人類審核通過後執行）

```bash
git mv proposals/onboarding/catalog-info.yaml .
mkdir -p .github/factory
git mv proposals/onboarding/risk-paths.yml .github/factory/risk-paths.yml
git rm -r proposals/onboarding
```

> 注意：`catalog-info.yaml` 與 `.github/factory/risk-paths.yml` 是 guardrail 本身，
> 依 docs/05 §1.1 agent **不得**代寫——必須由人類審核後親手執行上列搬檔。

## 四、合併後的驗證步驟（docs/16 §5.2 雙向探測）

開一個 `factory/*` 探測 PR 後，以 `factory-rescore` 跨 repo 實測**兩個方向**（只驗一般
檔案無法區分「硬規則正確」與「硬規則根本沒載入」）：

```bash
# 方向一：只改一般檔案 → 預期 triggeredHardRules 為空、不升級
gh workflow run factory-rescore.yml --repo philipz/software_factory \
  -f repo=philipz/camunda_hazelcast -f base_branch=software-factory -f pr_number=<PR>

# 方向二：改一個硬規則路徑（如 src/main/resources/application.yaml）→ 預期 H3 出現、升級
gh workflow run factory-rescore.yml --repo philipz/software_factory \
  -f repo=philipz/camunda_hazelcast -f base_branch=software-factory -f pr_number=<PR>
```

| 探測內容 | 預期 |
|---|---|
| 只改一般檔案 | 基準分、`triggeredHardRules: []`、`escalated: false` |
| 改 `src/main/resources/application*.yaml` | 分數上升、`H3` 出現、`escalated: true` |

## 五、risk-paths 撰寫取捨說明

H1–H4、H6–H7 對本 repo **皆無實際對應結構**，依 docs/16 §5.3 教訓**保留 id 但留空陣列**
並註明「掃描未發現，待人類確認」，**不複製範本的 `**/*token*` 等易誤中 glob**。唯一寫入
實際路徑的是 **H3**（`src/main/resources/application*.yaml`，內嵌憑證）。H5 無條件寫入。
