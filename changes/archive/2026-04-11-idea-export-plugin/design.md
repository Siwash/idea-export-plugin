> 来源: spec.md
> 生成时间: 2026-04-11
> 阶段: design

### Why

**背景与现状**

当前仓库仅有 `proposal.md` 与 `spec.md`，未扫描到插件源码、`plugin.xml`、构建脚本或现成模块；用户提供的旧目录仅保留 `lib` 产物，也未找到可复用源码与构建定义。因此本次设计按“从零重建 IntelliJ 插件”处理。

目标版本为 IntelliJ IDEA IU-242 及以上，运行环境为 JBR/JDK 21。插件需要同时处理项目树、编辑器、Git 变更列表三类入口，并把导出路径历史持久化到用户目录，实现跨项目共享。

**设计目标 / 非目标**

| 类型 | 说明 |
|------|------|
| ✅ 目标 | 使用一套导出主流程覆盖项目树、编辑器、Git 变更列表三类入口 |
| ✅ 目标 | 支持普通补丁与客户 bug jar 两种目录规划，并支持多工程聚合导出 |
| ✅ 目标 | 将历史导出路径持久化到用户目录文件，满足 30 条、去重、LRU、空配置自动创建 |
| ✅ 目标 | 提供 Maven 当前工程编译与 IDEA 编译两条执行路径，默认 Maven |
| ❌ 非目标 | 不做导入、回滚、差异比对、第三类特殊打包结构识别 |
| ❌ 非目标 | 不兼容旧插件内部实现；旧产物只作为背景，不做反编译复刻 |

### What

#### 技术方案

**架构决策**

| 模块 | 职责 | 依赖 |
|------|------|------|
| `action` | 对接 IDEA 三类右键入口，把 `AnActionEvent` 交给统一导出服务 | IntelliJ ActionSystem, `service` |
| `service` | 编排选择解析、参数确认、编译、导出、结果通知 | `model`, `storage`, `ui`, IntelliJ OpenAPI |
| `resolver` | 将项目树/编辑器/Git 变更列表中的选择统一解析为可导出文件清单与所属模块 | IntelliJ Project/VFS/VCS API, `model` |
| `planner` | 根据导出模式生成普通补丁或 bug jar 的目标目录与复制计划 | `model`, Maven metadata |
| `storage` | 读写用户目录下的历史路径文件，处理去重、LRU、自动创建 | JDK NIO, JSON |
| `ui` | 展示导出参数面板与结果摘要 | IntelliJ DialogWrapper, `model` |
| `test` | 验证路径历史、目录映射、bug jar 规划、选择聚合逻辑 | JUnit |

模块关系：三个入口 Action 只负责采集事件并调用 `ExportCommandService`；`ExportCommandService` 统一串联“选择解析 → 参数弹窗 → 编译 → 导出规划 → 文件复制 → 结果通知”；目录映射与历史路径均独立成服务，避免把规则散落在各入口类中。

**数据模型变更**

| 操作 | 表/实体 | 字段 | 类型 | 约束 | 说明 |
|------|---------|------|------|------|------|
| 新增 | `ExportRequest` | `mode` / `compileMode` / `targetPath` / `selectedItems` | enum / enum / String / List | 必填 | 一次导出请求的输入模型 |
| 新增 | `SelectedItem` | `moduleName` / `moduleBasePath` / `sourcePath` / `sourceType` | String / String / String / enum | 必填 | 统一表示项目树、编辑器、Git 变更列表中的选中项 |
| 新增 | `ExportEntry` | `sourcePath` / `outputPath` / `moduleName` / `status` / `message` | String / String / String / enum / String | 必填 | 单文件导出计划与执行结果 |
| 新增 | `CompileResult` | `success` / `summary` / `compiledModules` | boolean / String / List | 必填 | 编译阶段输出 |
| 新增 | `RecentPathState` | `paths` | List<String> | 最大 30 条 | 历史导出路径持久化模型 |
| 新增 | `ModulePackagingInfo` | `moduleName` / `finalName` / `outputDirectory` | String / String / String | bug jar 模式必填 | 当前工程打包元数据 |

实体关系：`ExportRequest 1:N SelectedItem`，`ExportRequest 1:N ExportEntry`；`ModulePackagingInfo` 按模块聚合后参与 bug jar 目录规划；`RecentPathState` 为全局单例文件，不与项目绑定。

**接口定义**

| 接口 | 方法 | 路径/签名 | 入参 | 出参 | 说明 |
|------|------|----------|------|------|------|
| `AbstractExportToSeeyonAction` | `actionPerformed` | `public void actionPerformed(@NotNull AnActionEvent event)` | `AnActionEvent` | `void` | 三类入口的公共触发方法 |
| `AbstractExportToSeeyonAction` | `update` | `public void update(@NotNull AnActionEvent event)` | `AnActionEvent` | `void` | 控制菜单显隐与可用状态 |
| `ExportCommandService` | `startExport` | `public void startExport(@NotNull Project project, @NotNull AnActionEvent event)` | `Project`, `AnActionEvent` | `void` | 统一编排整个导出流程 |
| `SelectionResolver` | `resolve` | `public List<SelectedItem> resolve(@NotNull Project project, @NotNull AnActionEvent event) throws ExportException` | `Project`, `AnActionEvent` | `List<SelectedItem>` | 解析三类入口中的选中项 |
| `ExportDialog` | `showAndGetRequest` | `public Optional<ExportRequest> showAndGetRequest(@NotNull Project project, @NotNull List<SelectedItem> selectedItems, @NotNull List<String> recentPaths)` | `Project`, `selectedItems`, `recentPaths` | `Optional<ExportRequest>` | 采集导出模式、导出路径、编译方式 |
| `CompileService` | `compile` | `public CompileResult compile(@NotNull Project project, @NotNull ExportRequest request) throws ExportException` | `Project`, `ExportRequest` | `CompileResult` | 根据 `compileMode` 执行编译 |
| `CompileService` | `compileWithMavenCurrentModule` | `protected CompileResult compileWithMavenCurrentModule(@NotNull Project project, @NotNull Set<String> moduleBasePaths) throws ExportException` | `Project`, `moduleBasePaths` | `CompileResult` | 默认编译路径，只编译涉及模块 |
| `CompileService` | `compileWithIdea` | `protected CompileResult compileWithIdea(@NotNull Project project, @NotNull Collection<String> moduleNames) throws ExportException` | `Project`, `moduleNames` | `CompileResult` | 可选 IDEA 编译路径 |
| `PackagingMetadataService` | `resolvePackaging` | `public Map<String, ModulePackagingInfo> resolvePackaging(@NotNull Project project, @NotNull List<SelectedItem> items) throws ExportException` | `Project`, `items` | `Map<String, ModulePackagingInfo>` | 解析模块打包名与输出目录 |
| `PatchLayoutService` | `plan` | `public List<ExportEntry> plan(@NotNull ExportRequest request, @NotNull Map<String, ModulePackagingInfo> packagingInfo) throws ExportException` | `ExportRequest`, `packagingInfo` | `List<ExportEntry>` | 生成普通补丁或 bug jar 的导出计划 |
| `FileExportService` | `export` | `public ExportSummary export(@NotNull List<ExportEntry> entries) throws ExportException` | `entries` | `ExportSummary` | 执行目录创建与文件复制 |
| `RecentPathStore` | `load` | `public List<String> load() throws IOException` | — | `List<String>` | 读取用户目录中的历史路径 |
| `RecentPathStore` | `record` | `public void record(@NotNull String path) throws IOException` | `path` | `void` | 写入历史路径并执行去重、LRU、裁剪 |
| `NotificationService` | `notifyResult` | `public void notifyResult(@NotNull Project project, @NotNull ExportSummary summary)` | `Project`, `ExportSummary` | `void` | 展示成功/失败摘要 |

**错误处理策略**

| 错误类型 | 处理方式 | HTTP状态码/异常类 |
|---------|---------|----------------|
| 未选择可导出文件 | Action 保持可见但执行后直接提示并终止 | `ExportException` |
| 选择项含不支持类型 | 解析阶段标记为 skipped，结果摘要中列出原因 | `ExportEntryStatus.SKIPPED` |
| 用户目录配置缺失 | 自动创建目录与空 JSON 文件后继续 | 不抛错 |
| 用户目录配置损坏 | 备份损坏文件，重建空配置并提示已重置历史记录 | `ExportException` |
| Maven 编译失败 | 终止导出，展示错误摘要与模块名 | `ExportException` |
| IDEA 编译失败 | 终止导出，展示错误摘要与模块名 | `ExportException` |
| bug jar 模式无法确定 jar 文件夹名 | 终止导出，提示该模块缺少可解析打包名 | `ExportException` |
| 导出路径不可写 | 导出前校验失败并终止 | `ExportException` |
| 单文件复制失败 | 继续处理其他文件，最终结果输出成功/失败明细 | `ExportEntryStatus.FAILED` |

#### 关键决策与理由

| 决策 | 可选方案 | 选择 | 理由 |
|------|---------|------|------|
| 插件工程构建方式 | A: Gradle IntelliJ Platform / B: Maven 自拼插件打包 | A | 当前仓库无现成构建定义，Gradle 对 IU-242 新版插件构建最直接，插件签名、依赖、运行 IDE 配置更简单；不选 B 是因为初始化成本更高且维护复杂 |
| 三类入口的处理方式 | A: 每个入口各自实现完整流程 / B: 入口只负责转发到统一服务 | B | 三个入口的差异只在选择来源，统一主流程可避免规则分叉；不选 A 是因为导出规则、错误提示、历史路径逻辑会重复 |
| 历史路径存储位置 | A: Project 级配置 / B: IDE 设置存储 / C: 用户目录 JSON 文件 | C | 需求明确要求存放在 user 用户文件夹，且要跨项目共享；不选 A 是因为项目隔离，不选 B 是因为位置不可见且不满足显式文件要求 |
| 历史路径格式 | A: Properties / B: JSON | B | 只需存一组路径，JSON 足够简单且便于后续扩展与损坏恢复；不选 A 是因为 LRU 顺序和备份可读性较差 |
| bug jar 文件夹名获取方式 | A: 固定使用模块名 / B: 解析 Maven 打包元数据 `finalName` / `artifactId` | B | 用户要求“jar 是工程打包为 jar 的文件夹名称”；解析打包元数据最贴近真实产物名；不选 A 是因为模块名和最终 jar 名可能不一致 |
| jar 名解析失败时的行为 | A: 回退模块名继续导出 / B: 直接失败并提示 | B | 需求强调目录格式正确，错误回退会产生错误补丁结构；不选 A 是因为会掩盖配置问题 |
| 编译分支实现方式 | A: `CompileService` 内部按枚举分支 / B: 引入多策略接口层级 | A | 当前只有 Maven 和 IDEA 两种模式，用一个服务内部分支即可满足需求；不选 B 是因为会引入不必要抽象 |
| UI 方案 | A: `DialogWrapper` + Swing 表单 / B: 自定义 ToolWindow | A | 这是一次性操作面板，不需要常驻窗口；不选 B 是因为交互更重、实现更复杂 |

#### 风险与权衡

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| IntelliJ 242+ 的 VCS/Action 上下文在不同入口返回的数据结构不同 | 中 | 高 | `SelectionResolver` 先按入口类型分支，再统一归一化为 `SelectedItem`；为三类入口分别补测试 |
| Maven 当前工程编译在复杂多模块依赖下可能仍需上游模块产物 | 中 | 高 | 仅对涉及模块发起编译；若 Maven 返回依赖缺失，直接提示切换 IDEA 编译，不静默降级 |
| Java 源文件导出若只复制顶层 `.class`，可能漏掉内部类/匿名类 | 中 | 中 | 规划阶段按编译输出目录扫描同名前缀的 `.class` 文件，一并纳入 `ExportEntry` |
| 用户目录配置文件被手动破坏后影响历史路径读取 | 中 | 中 | 读取失败时先备份坏文件，再重建空配置，并向用户提示历史已重置 |
| bug jar 名解析依赖 Maven 元数据，非 Maven 工程无法满足该模式 | 低 | 中 | 普通补丁模式不受影响；bug jar 模式下预校验工程元数据，失败时阻止导出并明确原因 |

无数据迁移、无服务端 API、无灰度发布要求，本设计不需要 Rollout 章节。

#### 变更文件清单

| 文件路径 | 操作 | 变更说明 |
|---------|------|---------|
| `settings.gradle.kts` | 新增 | 定义插件工程名 |
| `build.gradle.kts` | 新增 | 定义 IntelliJ 平台版本、JDK 21、测试依赖 |
| `src/main/resources/META-INF/plugin.xml` | 新增 | 注册插件信息与三类 Action |
| `src/main/java/com/seeyon/ideaexport/action/AbstractExportToSeeyonAction.java` | 新增 | 抽取公共 Action 入口逻辑 |
| `src/main/java/com/seeyon/ideaexport/action/ProjectViewExportAction.java` | 新增 | 项目树右键入口 |
| `src/main/java/com/seeyon/ideaexport/action/EditorExportAction.java` | 新增 | 编辑器右键入口 |
| `src/main/java/com/seeyon/ideaexport/action/ChangesViewExportAction.java` | 新增 | Git 变更列表右键入口 |
| `src/main/java/com/seeyon/ideaexport/service/ExportCommandService.java` | 新增 | 统一导出主流程 |
| `src/main/java/com/seeyon/ideaexport/service/CompileService.java` | 新增 | Maven / IDEA 编译执行 |
| `src/main/java/com/seeyon/ideaexport/service/PackagingMetadataService.java` | 新增 | 解析模块打包元数据 |
| `src/main/java/com/seeyon/ideaexport/service/PatchLayoutService.java` | 新增 | 生成普通补丁与 bug jar 导出计划 |
| `src/main/java/com/seeyon/ideaexport/service/FileExportService.java` | 新增 | 创建目录并复制文件 |
| `src/main/java/com/seeyon/ideaexport/service/NotificationService.java` | 新增 | 成功/失败结果通知 |
| `src/main/java/com/seeyon/ideaexport/resolver/SelectionResolver.java` | 新增 | 统一解析三类入口选择 |
| `src/main/java/com/seeyon/ideaexport/storage/RecentPathStore.java` | 新增 | 用户目录历史路径读写 |
| `src/main/java/com/seeyon/ideaexport/ui/ExportDialog.java` | 新增 | 导出参数面板 |
| `src/main/java/com/seeyon/ideaexport/model/ExportRequest.java` | 新增 | 导出请求模型 |
| `src/main/java/com/seeyon/ideaexport/model/SelectedItem.java` | 新增 | 选中项模型 |
| `src/main/java/com/seeyon/ideaexport/model/ExportEntry.java` | 新增 | 导出项模型 |
| `src/main/java/com/seeyon/ideaexport/model/ExportSummary.java` | 新增 | 导出汇总模型 |
| `src/main/java/com/seeyon/ideaexport/model/RecentPathState.java` | 新增 | 历史路径模型 |
| `src/main/java/com/seeyon/ideaexport/model/ModulePackagingInfo.java` | 新增 | 打包信息模型 |
| `src/test/java/com/seeyon/ideaexport/storage/RecentPathStoreTest.java` | 新增 | 历史路径 LRU/去重测试 |
| `src/test/java/com/seeyon/ideaexport/service/PatchLayoutServiceTest.java` | 新增 | 普通补丁与 bug jar 路径规划测试 |
| `src/test/java/com/seeyon/ideaexport/resolver/SelectionResolverTest.java` | 新增 | 多入口选择归一化测试 |

### How

#### 任务拆分

| 任务名称 | 详细描述 | 关联设计章节 | 计划工作量(人天) |
|----------|---------|------------|--------------|
| 【骨架】(后端) 初始化 IntelliJ 插件工程骨架 | 1. 新建 Gradle 插件工程<br>2. 配置 IU-242 与 JDK 21<br>3. 注册 `plugin.xml` 与基础依赖<br>4. 建立基础包结构与测试骨架 | 架构决策 / 变更文件清单 | 1 |
| 【入口】(全栈) 实现三类 Action 入口与统一命令编排 | 1. 增加项目树右键 Action<br>2. 增加编辑器右键 Action<br>3. 增加 Git 变更列表右键 Action<br>4. 三类入口统一调用 `ExportCommandService` | 架构决策 / 接口定义 | 1 |
| 【选择】(后端) 实现多工程选择归一化 | 1. 解析项目树选择<br>2. 解析编辑器当前文件<br>3. 解析 Git 变更列表选择<br>4. 统一输出 `SelectedItem` 并过滤不支持类型 | 接口定义 / 错误处理策略 | 1.5 |
| 【路径】(后端) 实现普通补丁与 bug jar 目录规划 | 1. 实现 `classes` 与 `/seeyon` 映射<br>2. 实现 bug jar 目录规划<br>3. 解析模块打包元数据<br>4. 输出 `ExportEntry` 明细 | 数据模型变更 / 接口定义 / 关键决策与理由 | 1.5 |
| 【编译】(后端) 实现 Maven 与 IDEA 编译分支 | 1. 默认执行 Maven 当前工程编译<br>2. 支持切换 IDEA 编译<br>3. 编译失败时生成可读摘要<br>4. 仅对涉及模块执行编译 | 接口定义 / 风险与权衡 | 1.5 |
| 【历史】(全栈) 实现用户目录历史路径存储与面板 | 1. `DialogWrapper` 收集导出参数<br>2. 历史路径跨项目共享展示<br>3. 实现 30 条上限、去重、LRU<br>4. 配置缺失或损坏时自动恢复 | 数据模型变更 / 接口定义 / 错误处理策略 | 1.5 |
| 【执行】(后端) 实现文件复制与结果通知 | 1. 创建目标目录<br>2. 执行复制并汇总成功/失败项<br>3. 支持部分成功部分失败摘要<br>4. 导出结束后写回历史路径 | 接口定义 / 错误处理策略 | 1 |
| 【联调】联调 + 功能测试 | 1. 覆盖三类入口<br>2. 覆盖普通补丁与 bug jar 两类模式<br>3. 覆盖单工程与多工程聚合导出<br>4. 覆盖历史路径、编译失败、部分成功场景 | 风险与权衡 / 变更文件清单 | 1 |
| **合计** | | | **10** |

**任务依赖**（如有）：

```text
- 【骨架】(后端) 初始化 IntelliJ 插件工程骨架
- 【入口】(全栈) 实现三类 Action 入口与统一命令编排 ← depends: 【骨架】
- 【选择】(后端) 实现多工程选择归一化 ← depends: 【骨架】
- 【路径】(后端) 实现普通补丁与 bug jar 目录规划 ← depends: 【选择】
- 【编译】(后端) 实现 Maven 与 IDEA 编译分支 ← depends: 【选择】
- 【历史】(全栈) 实现用户目录历史路径存储与面板 ← depends: 【骨架】
- 【执行】(后端) 实现文件复制与结果通知 ← depends: 【路径】, 【编译】, 【历史】
- 【联调】联调 + 功能测试 ← depends: 【入口】, 【执行】
```

### Verify

```text
设计自检：
- [x] 所有 spec 功能需求都有对应的技术方案
- [x] 所有技术决策都有理由
- [x] 接口定义完整（入参、出参、异常）
- [x] 数据模型变更明确（新增/修改/删除）
- [x] 任务拆分覆盖全部设计内容
- [x] 任务总量与需求规模匹配
- [x] 无实现代码（只有签名和结构）
- [x] 已按 .best-practices/ 约束检查 Risks/Rollout
```

### Impact

- 涉及模块1：新增 IntelliJ 插件工程骨架与 `plugin.xml` 注册
- 涉及模块2：新增统一导出服务、选择解析、路径规划、编译服务、历史路径存储
- 涉及模块3：新增 `DialogWrapper` 参数面板与导出结果通知
- 数据库变更：否
- 外部依赖变更：是，新增 IntelliJ Platform 构建依赖并调用本地 Maven 命令