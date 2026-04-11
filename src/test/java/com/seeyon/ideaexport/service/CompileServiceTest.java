package com.seeyon.ideaexport.service;

import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编译服务测试，验证默认 Maven 编译分支、关闭编译开关和关键失败路径。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class CompileServiceTest {

    @TempDir
    Path tempDir;

    /**
     * 验证默认 Maven 编译会按选中模块目录聚合执行，并返回输出目录。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldCompileSelectedModulesWithMavenMode() throws Exception {
        Path moduleDirectory = tempDir.resolve("demo-module");
        Files.createDirectories(moduleDirectory);
        Files.writeString(moduleDirectory.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        CompileService compileService = new StubCompileService();
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                false,
                tempDir.resolve("output"),
                List.of(new SelectedItem("demo-module", moduleDirectory, moduleDirectory.resolve("src/main/java/demo/Test.java"), "demo/Test.class", SourceType.JAVA_SOURCE))
        );

        CompileResult result = compileService.compile(createMockProject(), request);

        // 默认分支必须走 Maven 当前工程编译，不能误切到 IDEA 编译。
        assertEquals(true, result.success());
        assertEquals(List.of("demo-module"), result.compiledModules());
        assertEquals(moduleDirectory.resolve("target/classes"), result.moduleOutputDirectories().get("demo-module"));
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
                true,
                tempDir.resolve("output"),
                List.of(new SelectedItem("demo-module", moduleDirectory, moduleDirectory.resolve("src/main/java/demo/Test.java"), "demo/Test.class", SourceType.JAVA_SOURCE))
        );

        CompileResult result = compileService.compile(createMockProject(), request);

        // 关闭编译时必须直接复用现有产物目录，并清晰告知用户已跳过编译。
        assertEquals("已跳过编译，直接使用现有产物", result.summary());
        assertEquals(moduleDirectory.resolve("target/classes"), result.moduleOutputDirectories().get("demo-module"));
    }

    /**
     * 验证默认 Maven 编译在 pom 缺失时直接失败。
     */
    @Test
    void shouldFailWhenPomMissingForMavenCompile() {
        CompileService compileService = new StubCompileService();
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                false,
                tempDir.resolve("output"),
                List.of(new SelectedItem("demo-module", tempDir.resolve("missing-module"), tempDir.resolve("missing-module/src/main/java/demo/Test.java"), "demo/Test.class", SourceType.JAVA_SOURCE))
        );

        // pom 缺失时必须立即失败，否则用户会误以为当前工程已经完成编译。
        assertThrows(ExportException.class, () -> compileService.compile(createMockProject(), request));
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
     * 使用子类替换外部命令执行，避免单元测试依赖本机 Maven 环境。
     */
    private static class StubCompileService extends CompileService {

        /**
         * 返回固定成功结果，专注验证默认分支的模块聚合逻辑。
         *
         * @param project 当前项目
         * @param moduleBasePaths 涉及模块目录
         * @return 固定成功结果
         * @throws ExportException pom 缺失时抛出
         */
        @Override
        protected CompileResult compileWithMavenCurrentModule(Project project, Set<String> moduleBasePaths) throws ExportException {
            for (String moduleBasePath : moduleBasePaths) {
                if (!Files.exists(Path.of(moduleBasePath).resolve("pom.xml"))) {
                    throw new ExportException("当前工程不是 Maven 模块，无法执行默认编译: " + moduleBasePath);
                }
            }
            Map<String, Path> outputDirectories = moduleBasePaths.stream().collect(
                    java.util.stream.Collectors.toMap(
                            path -> Path.of(path).getFileName().toString(),
                            path -> Path.of(path).resolve("target").resolve("classes"),
                            (left, right) -> left,
                            java.util.LinkedHashMap::new
                    )
            );
            // 测试只验证选择聚合和默认分支，不依赖真实外部编译命令。
            return CompileResult.success(moduleBasePaths.stream().map(path -> Path.of(path).getFileName().toString()).toList(), outputDirectories, "Maven 当前工程编译成功");
        }
    }
}
