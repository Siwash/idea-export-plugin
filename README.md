# Idea Export Seeyon

Idea Export Seeyon 是一个 IntelliJ IDEA 插件，用于从项目视图、编辑器、Git 变更列表和 Git 历史中选择文件，并导出符合 Seeyon 补丁目录结构的文件。插件会按需编译选中模块，将编译产物或源码映射到目标目录，并在 IDEA 内展示清晰的导出结果。

## 功能特性

- 支持从 Project View、编辑器右键菜单、Git Changes、Git 历史和 Git 上下文菜单发起导出。
- 支持普通补丁、客户 bug jar、源码导出三种模式。
- Java 类文件导出到 `seeyon/WEB-INF/classes`，Web 资源导出到 `seeyon/`。
- 客户 bug jar 模式根据模块 `artifactId` 解析 jar 目录，并导出到 `seeyon/WEB-INF/lib/<jar-name>.jar/`。
- 支持导出前使用 Maven 或 IDEA Compiler 编译。
- Maven 编译支持串行模块顺序和并行多线程两种策略。
- 支持跳过编译，直接复用已有 `target/classes` 产物。
- 导出路径历史跨 IDEA 项目共享保存。
- 导出完成后展示成功项、失败项、跳过项、编译日志和可点击错误位置。
- 支持选择 Java 文件后执行 Maven 编译，并通过 JDI `redefineClasses` 热部署到当前活跃 Debug 会话。

## 环境要求

- IntelliJ IDEA Ultimate 2024.2.3 或更高版本。
- JDK 21。
- 本机已安装 Gradle。本仓库当前未包含 Gradle Wrapper 文件。
- 使用 Maven 编译或热部署时，IDEA 进程环境中需要能访问 Maven。

## 构建

运行测试：

```powershell
gradle test
```

构建插件 zip：

```powershell
gradle buildPlugin
```

插件包输出位置：

```text
build/distributions/idea-export-plugin-1.0.0-SNAPSHOT.zip
```

## 安装

1. 使用 `gradle buildPlugin` 构建插件 zip。
2. 打开 IntelliJ IDEA。
3. 进入 `Settings | Plugins`。
4. 点击齿轮图标，选择 `Install Plugin from Disk...`。
5. 选择 `build/distributions/` 下生成的 zip 文件并重启 IDEA。

## 使用方式

### 导出补丁

1. 在项目视图、当前编辑器、Git Changes 或 Git 历史中选择一个或多个文件。
2. 右键选择 `导出到 seeyon`。
3. 选择导出模式、编译方式、Maven 编译策略和目标目录。
4. 确认后等待后台任务完成。
5. 在结果窗口查看导出文件、跳过文件、失败原因和构建日志。

### 热部署

1. 先以 Debug 模式启动目标应用。
2. 选择 `src/main/java` 下的 Java 源文件。
3. 右键选择 `Maven 编译并热部署`。
4. 插件会编译选中文件所在模块，并把已加载类热替换到当前活跃 Debug 会话。

## 导出目录结构

### 普通补丁

Java 源文件会先编译成 class 文件，再复制到：

```text
<target>/seeyon/WEB-INF/classes/<package-path>/<ClassName>.class
```

Web 资源会复制到 Seeyon 根目录：

```text
<target>/seeyon/<webapp-relative-path>
```

### 客户 Bug Jar

Java class 文件会复制到根据模块打包信息解析出的 jar 目录：

```text
<target>/seeyon/WEB-INF/lib/<jar-name>.jar/<package-path>/<ClassName>.class
```

### 源码导出

源码导出模式不会执行编译，会直接复制原始选中文件：

```text
<target>/source/<module-name>/<module-relative-path>
```

## 开发说明

项目结构：

```text
src/main/java/com/seeyon/ideaexport/action      IDEA Action 和右键菜单入口
src/main/java/com/seeyon/ideaexport/resolver    IDEA 上下文选择解析
src/main/java/com/seeyon/ideaexport/service     编译、路径规划、导出、通知、热部署服务
src/main/java/com/seeyon/ideaexport/model       请求、结果、模式和状态模型
src/main/java/com/seeyon/ideaexport/ui          导出参数窗口和结果窗口
src/main/resources/META-INF/plugin.xml          IntelliJ 插件描述文件
src/test/java/com/seeyon/ideaexport             单元测试
```

常用 Gradle 任务：

```powershell
gradle test
gradle runIde
gradle buildPlugin
```

## 兼容性

插件描述文件通过 `patchPluginXml` 设置 `sinceBuild = 242`，对应 IntelliJ Platform 2024.2 及后续兼容版本。

## 许可证

本项目使用 MIT License。详情见 [LICENSE](LICENSE)。
