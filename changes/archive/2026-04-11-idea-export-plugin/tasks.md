# 任务清单

> 来源: design.md
> 生成时间: 2026-04-11

## 实施任务

- [x] 【骨架】(后端) 初始化 IntelliJ 插件工程骨架
  - 目标: 创建可运行的 IntelliJ 插件工程，补齐构建脚本、plugin.xml、基础包结构与测试骨架。
  - 涉及文件: settings.gradle.kts, build.gradle.kts, src/main/resources/META-INF/plugin.xml
  - 预期结果: 项目具备插件开发与测试的基础结构，后续功能代码可直接接入。
- [x] 【入口】(全栈) 实现三类 Action 入口与统一命令编排
  - 目标: 提供项目树、编辑器、Git 变更列表三个入口，并统一进入导出主流程。
  - 涉及文件: src/main/java/com/seeyon/ideaexport/action/*.java, src/main/java/com/seeyon/ideaexport/service/ExportCommandService.java
  - 预期结果: 三类入口都能触发同一导出流程。
- [x] 【选择】(后端) 实现多工程选择归一化
  - 目标: 把不同入口的选择统一解析成可导出的标准模型，并过滤不支持类型。
  - 涉及文件: src/main/java/com/seeyon/ideaexport/resolver/SelectionResolver.java, src/main/java/com/seeyon/ideaexport/model/SelectedItem.java
  - 预期结果: 后续流程拿到统一的选中项数据，不再关心入口差异。
- [x] 【路径】(后端) 实现普通补丁与 bug jar 目录规划
  - 目标: 根据导出模式和文件类型生成目标路径，支持 classes、/seeyon、bug jar 三种映射规则。
  - 涉及文件: src/main/java/com/seeyon/ideaexport/service/PatchLayoutService.java, src/main/java/com/seeyon/ideaexport/service/PackagingMetadataService.java, src/main/java/com/seeyon/ideaexport/model/ExportEntry.java
  - 预期结果: 导出前可以得到完整、可执行的导出计划。
- [x] 【编译】(后端) 实现 Maven 与 IDEA 编译分支
  - 目标: 支持默认 Maven 当前工程编译与可选 IDEA 编译，并给出可读失败摘要。
  - 涉及文件: src/main/java/com/seeyon/ideaexport/service/CompileService.java, src/main/java/com/seeyon/ideaexport/model/CompileResult.java
  - 预期结果: 导出前可按所选模式完成编译并把结果返回主流程。
- [x] 【历史】(全栈) 实现用户目录历史路径存储与面板
  - 目标: 提供导出参数面板和共享历史路径存储，满足 30 条、去重、LRU、自动恢复。
  - 涉及文件: src/main/java/com/seeyon/ideaexport/storage/RecentPathStore.java, src/main/java/com/seeyon/ideaexport/ui/ExportDialog.java, src/main/java/com/seeyon/ideaexport/model/RecentPathState.java
  - 预期结果: 用户可以选择导出路径并复用历史记录，配置缺失时不报错。
- [x] 【执行】(后端) 实现文件复制与结果通知
  - 目标: 执行导出计划、复制文件、汇总成功失败结果并通知用户，同时写回历史路径。
  - 涉及文件: src/main/java/com/seeyon/ideaexport/service/FileExportService.java, src/main/java/com/seeyon/ideaexport/service/NotificationService.java, src/main/java/com/seeyon/ideaexport/model/ExportSummary.java
  - 预期结果: 导出完成后用户能看到明确结果，支持部分成功部分失败。
- [x] 【联调】联调 + 功能测试
  - 目标: 验证三类入口、两类导出模式、多工程聚合、历史路径和失败处理行为。
  - 涉及文件: src/test/java/com/seeyon/ideaexport/storage/RecentPathStoreTest.java, src/test/java/com/seeyon/ideaexport/service/PatchLayoutServiceTest.java, src/test/java/com/seeyon/ideaexport/resolver/SelectionResolverTest.java
  - 预期结果: 核心逻辑有测试覆盖，整体流程符合 spec。

## 完成状态

> 进度: 8/8 已完成
