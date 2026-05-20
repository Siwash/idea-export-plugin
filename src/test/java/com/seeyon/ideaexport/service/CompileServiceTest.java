package com.seeyon.ideaexport.service;

import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.CompileStrategy;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编译服务测试，验证 Maven install 串并行调度、源码模式短路、关闭编译开关和关键失败路径。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
class CompileServiceTest {

    @TempDir
    Path tempDir;

    /**
     * 验证默认 Maven 串行编译会按请求中的模块顺序执行，并返回输出目录。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldCompileSelectedModulesWithSerialMavenStrategy() throws Exception {
        Path firstModule = createMavenModule("demo-module-a");
        Path secondModule = createMavenModule("demo-module-b");
        RecordingCompileService compileService = new RecordingCompileService();
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                tempDir.resolve("output"),
                List.of(secondModule, firstModule),
                List.of(
                        new SelectedItem("demo-module-a", firstModule, firstModule.resolve("src/main/java/demo/A.java"), "demo/A.class", "src/main/java/demo/A.java", SourceType.JAVA_SOURCE),
                        new SelectedItem("demo-module-b", secondModule, secondModule.resolve("src/main/java/demo/B.java"), "demo/B.class", "src/main/java/demo/B.java", SourceType.JAVA_SOURCE)
                )
        );

        CompileResult result = compileService.compile(createMockProject(), request);

        // 串行模式必须严格按用户调整后的模块顺序执行，否则模块依赖关系会失真。
        assertEquals(List.of(secondModule, firstModule), compileService.serialOrder);
        assertEquals(List.of("demo-module-b", "demo-module-a"), result.compiledModules());
        assertEquals(secondModule.resolve("target/classes"), result.moduleOutputDirectories().get("demo-module-b"));
        assertEquals("Maven 串行安装成功", result.summary());
    }

    /**
     * 验证源码模式会直接短路返回，不触发 Maven 或 IDEA 编译。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldSkipCompileStageForSourceExport() throws Exception {
        Path moduleDirectory = tempDir.resolve("demo-module");
        Files.createDirectories(moduleDirectory.resolve("src/main/java/demo"));
        RecordingCompileService compileService = new RecordingCompileService();
        ExportRequest request = new ExportRequest(
                ExportMode.SOURCE_EXPORT,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                tempDir.resolve("output"),
                List.of(moduleDirectory),
                List.of(new SelectedItem("demo-module", moduleDirectory, moduleDirectory.resolve("src/main/java/demo/Test.java"), "demo/Test.class", "src/main/java/demo/Test.java", SourceType.JAVA_SOURCE))
        );

        CompileResult result = compileService.compile(createMockProject(), request);

        // 源码模式不应再进入任何真实编译分支，否则会违背“直接复制源码”的规格。
        assertEquals(List.of(), compileService.serialOrder);
        assertEquals(List.of(), compileService.parallelOrder);
        assertEquals("源码导出无需编译", result.summary());
        assertEquals(Map.of(), result.moduleOutputDirectories());
    }

    /**
     * 验证关闭编译后会直接返回现有产物目录。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldSkipCompileWhenRequested() throws Exception {
        Path moduleDirectory = tempDir.resolve("demo-module");
        Files.createDirectories(moduleDirectory.resolve("target/classes"));
        CompileService compileService = new CompileService();
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                true,
                tempDir.resolve("output"),
                List.of(moduleDirectory),
                List.of(new SelectedItem("demo-module", moduleDirectory, moduleDirectory.resolve("src/main/java/demo/Test.java"), "demo/Test.class", "src/main/java/demo/Test.java", SourceType.JAVA_SOURCE))
        );

        CompileResult result = compileService.compile(createMockProject(), request);

        // 关闭编译时必须直接复用现有产物目录，并清晰告知用户已跳过编译。
        assertEquals("已跳过编译，直接使用现有产物", result.summary());
        assertEquals(moduleDirectory.resolve("target/classes"), result.moduleOutputDirectories().get("demo-module"));
    }

    /**
     * 验证 Maven 串行编译在 pom 缺失时直接失败。
     */
    @Test
    void shouldFailWhenPomMissingForMavenCompile() {
        CompileService compileService = new RecordingCompileService();
        Path missingModule = tempDir.resolve("missing-module");
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                tempDir.resolve("output"),
                List.of(missingModule),
                List.of(new SelectedItem("demo-module", missingModule, missingModule.resolve("src/main/java/demo/Test.java"), "demo/Test.class", "src/main/java/demo/Test.java", SourceType.JAVA_SOURCE))
        );

        // pom 缺失时必须立即失败，否则用户会误以为当前工程已经完成编译。
        assertThrows(ExportException.class, () -> compileService.compile(createMockProject(), request));
    }

    /**
     * 验证并行模式会走并行调度分支，而不是偷偷退回串行逻辑。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldUseParallelMavenStrategyWhenRequested() throws Exception {
        Path firstModule = createMavenModule("demo-module-a");
        Path secondModule = createMavenModule("demo-module-b");
        RecordingCompileService compileService = new RecordingCompileService();
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.PARALLEL,
                false,
                tempDir.resolve("output"),
                List.of(firstModule, secondModule),
                List.of(
                        new SelectedItem("demo-module-a", firstModule, firstModule.resolve("src/main/java/demo/A.java"), "demo/A.class", "src/main/java/demo/A.java", SourceType.JAVA_SOURCE),
                        new SelectedItem("demo-module-b", secondModule, secondModule.resolve("src/main/java/demo/B.java"), "demo/B.class", "src/main/java/demo/B.java", SourceType.JAVA_SOURCE)
                )
        );

        CompileResult result = compileService.compile(createMockProject(), request);

        // 并行模式必须走专门的并行分支，后续才能接入真实线程池调度。
        assertEquals(List.of(firstModule, secondModule), compileService.parallelOrder);
        assertEquals("Maven 并行安装成功", result.summary());
    }

    /**
     * 验证 Maven 真实命令已切换为 install，保证本地依赖可被后续模块消费。
     */
    @Test
    void shouldUseInstallGoalForMavenCommand() {
        CompileService compileService = new CompileService();
        Path pomPath = Path.of("/project/demo/pom.xml");
        ProcessBuilder processBuilder = compileService.createMavenProcessBuilder(pomPath);

        // Maven 目标必须改成 install，而不是 compile，否则多模块本地依赖链不稳定。
        assertEquals(List.of("cmd", "/c", "mvn", "-f", pomPath.toString(), "-DskipTests", "install"), processBuilder.command());
    }

    /**
     * 创建最小可用的 Maven 模块目录。
     *
     * @param moduleName 模块名
     * @return 模块目录
     * @throws Exception 创建失败
     */
    private Path createMavenModule(String moduleName) throws Exception {
        Path moduleDirectory = tempDir.resolve(moduleName);
        Files.createDirectories(moduleDirectory);
        Files.writeString(moduleDirectory.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        return moduleDirectory;
    }

    /**
     * 创建最小可用的 Project 占位对象，避免测试依赖真实 IDE 生命周期。
     *
     * @return MockProject
     */
    private Project createMockProject() {
        return new MockProject(null, new Disposable() {
            @Override
            public void dispose() {
                // 测试只需要一个最小可用的 Project 占位对象，不依赖真实释放逻辑。
            }
        });
    }

    /**
     * 记录型编译服务，用于隔离真实外部进程并验证调度入口是否正确。
     */
    private static class RecordingCompileService extends CompileService {

        private final List<Path> serialOrder = new ArrayList<>();
        private final List<Path> parallelOrder = new ArrayList<>();

        /**
         * 记录串行顺序并返回固定成功结果，专注验证顺序和请求传递。
         *
         * @param project 当前项目
         * @param orderedModuleBasePaths 有序模块目录
         * @param reporter 过程上报器
         * @return 固定成功结果
         * @throws ExportException pom 缺失时抛出
         */
        @Override
        protected CompileResult compileWithMavenSerial(Project project, List<Path> orderedModuleBasePaths, ExportRuntimeReporter reporter) throws ExportException {
            validatePomFiles(orderedModuleBasePaths);
            serialOrder.clear();
            serialOrder.addAll(orderedModuleBasePaths);
            return CompileResult.success(
                    orderedModuleBasePaths.stream().map(path -> path.getFileName().toString()).toList(),
                    orderedModuleBasePaths.stream().collect(
                            java.util.stream.Collectors.toMap(
                                    path -> path.getFileName().toString(),
                                    path -> path.resolve("target").resolve("classes"),
                                    (left, right) -> left,
                                    java.util.LinkedHashMap::new
                            )
                    ),
                    "Maven 串行安装成功"
            );
        }

        /**
         * 记录并行入口，证明主流程已正确分发到并行分支。
         *
         * @param project 当前项目
         * @param orderedModuleBasePaths 有序模块目录
         * @param reporter 过程上报器
         * @return 固定成功结果
         * @throws ExportException pom 缺失时抛出
         */
        @Override
        protected CompileResult compileWithMavenParallel(Project project, List<Path> orderedModuleBasePaths, ExportRuntimeReporter reporter) throws ExportException {
            validatePomFiles(orderedModuleBasePaths);
            parallelOrder.clear();
            parallelOrder.addAll(orderedModuleBasePaths);
            return CompileResult.success(
                    orderedModuleBasePaths.stream().map(path -> path.getFileName().toString()).toList(),
                    orderedModuleBasePaths.stream().collect(
                            java.util.stream.Collectors.toMap(
                                    path -> path.getFileName().toString(),
                                    path -> path.resolve("target").resolve("classes"),
                                    (left, right) -> left,
                                    java.util.LinkedHashMap::new
                            )
                    ),
                    "Maven 并行安装成功"
            );
        }

        /**
         * 统一校验 pom 是否存在，保证测试仍覆盖真实失败边界。
         *
         * @param modulePaths 模块目录列表
         * @throws ExportException pom 缺失时抛出
         */
        private void validatePomFiles(List<Path> modulePaths) throws ExportException {
            for (Path modulePath : modulePaths) {
                if (!Files.exists(modulePath.resolve("pom.xml"))) {
                    throw new ExportException("当前工程不是 Maven 模块，无法执行默认编译: " + modulePath);
                }
            }
        }
    }
}
