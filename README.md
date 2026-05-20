# Idea Export Seeyon

IntelliJ IDEA plugin for exporting Seeyon patch files from selected Java source files, web resources, and Git changes. It focuses on the common Seeyon patch workflow: compile selected modules, map build artifacts into the expected `seeyon/` layout, and show a readable export result inside IDEA.

## Features

- Export from Project View, Editor context menu, Git Changes view, Git history, and Git context menus.
- Support standard patch export, customer bug jar export, and source export.
- Map Java classes to `seeyon/WEB-INF/classes` and web resources to `seeyon/`.
- Map bug jar patches to `seeyon/WEB-INF/lib/<jar-name>.jar/` based on module packaging metadata.
- Compile before export with Maven or IDEA compiler.
- Choose Maven serial compile order for dependent multi-module projects, or parallel compile for faster independent modules.
- Skip compile and reuse existing `target/classes` outputs when needed.
- Persist recent export paths across IDEA projects.
- Show export result details, skipped files, failures, and clickable build error locations.
- Compile selected Java files and hot deploy classes to the active Debug session through JDI `redefineClasses`.

## Requirements

- IntelliJ IDEA Ultimate 2024.2.3 or later.
- JDK 21.
- Gradle installed locally. This repository does not currently include Gradle Wrapper files.
- Maven available from the IDEA process environment when using Maven compile or hot deploy.

## Build

Run tests:

```powershell
gradle test
```

Build the plugin zip:

```powershell
gradle buildPlugin
```

The plugin package is generated under:

```text
build/distributions/idea-export-plugin-1.0.0-SNAPSHOT.zip
```

## Install

1. Build the plugin zip with `gradle buildPlugin`.
2. Open IntelliJ IDEA.
3. Go to `Settings | Plugins`.
4. Click the gear icon and choose `Install Plugin from Disk...`.
5. Select the zip from `build/distributions/` and restart IDEA.

## Usage

### Export Patch

1. Select one or more files from Project View, the active editor, Git Changes, or Git history.
2. Right-click and choose `导出到 seeyon`.
3. Choose the export mode, compile mode, Maven compile strategy, and target directory.
4. Confirm the dialog and wait for the background task to finish.
5. Review the result dialog for exported files, skipped files, failures, and build logs.

### Hot Deploy

1. Start the target application in Debug mode.
2. Select Java source files under `src/main/java`.
3. Right-click and choose `Maven 编译并热部署`.
4. The plugin compiles the selected module with Maven and redefines loaded JVM classes in the active Debug session.

## Export Layout

### Standard Patch

Java source files are compiled and copied as class files:

```text
<target>/seeyon/WEB-INF/classes/<package-path>/<ClassName>.class
```

Web resources are copied under the Seeyon root:

```text
<target>/seeyon/<webapp-relative-path>
```

### Customer Bug Jar

Java class files are copied into a jar-shaped directory resolved from module packaging metadata:

```text
<target>/seeyon/WEB-INF/lib/<jar-name>.jar/<package-path>/<ClassName>.class
```

### Source Export

Original selected files are copied without compilation:

```text
<target>/source/<module-name>/<module-relative-path>
```

## Development

Project layout:

```text
src/main/java/com/seeyon/ideaexport/action      IDEA actions and context menu entries
src/main/java/com/seeyon/ideaexport/resolver    Selection resolution from IDEA contexts
src/main/java/com/seeyon/ideaexport/service     Compile, layout, export, notification, hot deploy services
src/main/java/com/seeyon/ideaexport/model       Request, result, mode, and state models
src/main/java/com/seeyon/ideaexport/ui          Export and result dialogs
src/main/resources/META-INF/plugin.xml          IntelliJ plugin descriptor
src/test/java/com/seeyon/ideaexport             Unit tests
```

Useful Gradle tasks:

```powershell
gradle test
gradle runIde
gradle buildPlugin
```

## Compatibility

The plugin descriptor is patched with `sinceBuild = 242`, matching IntelliJ Platform 2024.2 based IDEs and later compatible builds.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
