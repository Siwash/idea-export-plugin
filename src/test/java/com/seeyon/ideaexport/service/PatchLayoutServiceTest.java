package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.CompileStrategy;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.ModulePackagingInfo;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 导出路径规划测试，验证普通补丁、bug jar、源码导出和关闭编译后的路径映射规则。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class PatchLayoutServiceTest {

    private final PatchLayoutService patchLayoutService = new PatchLayoutService();

    /**
     * 验证普通 Java 类文件会映射到 seeyon/WEB-INF/classes 目录。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldMapJavaSourceToSeeyonWebInfClassesDirectory() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "comi-biz",
                Path.of("/project/comi-biz"),
                Path.of("/project/comi-biz/src/main/java/demo/Test.java"),
                Path.of("demo/Test.class").toString(),
                Path.of("src/main/java/demo/Test.java").toString(),
                SourceType.JAVA_SOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(
                List.of("comi-biz"),
                Map.of("comi-biz", Path.of("/project/comi-biz/target/classes"))
        );

        List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, Map.of());

        // 普通 Java 类导出必须进入 seeyon/WEB-INF/classes，才能匹配正式环境类加载目录。
        assertEquals(Path.of("/export/seeyon/WEB-INF/classes/demo/Test.class"), entries.get(0).outputPath());
        assertEquals(Path.of("/project/comi-biz/target/classes/demo/Test.class"), entries.get(0).sourcePath());
    }

    /**
     * 验证源码导出会直接复制原始 Java 文件到 source/模块名/模块相对路径。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldMapJavaSourceToSourceDirectoryInSourceExportMode() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "comi-biz",
                Path.of("/project/comi-biz"),
                Path.of("/project/comi-biz/src/main/java/demo/Test.java"),
                Path.of("demo/Test.class").toString(),
                Path.of("src/main/java/demo/Test.java").toString(),
                SourceType.JAVA_SOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.SOURCE_EXPORT,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );

        List<ExportEntry> entries = patchLayoutService.plan(request, CompileResult.success(List.of(), Map.of(), "源码导出无需编译"), Map.of());

        // 源码模式必须保留原始 .java 与模块相对路径，不能再落到 classes 目录。
        assertEquals(Path.of("/project/comi-biz/src/main/java/demo/Test.java"), entries.get(0).sourcePath());
        assertEquals(Path.of("/export/source/comi-biz/src/main/java/demo/Test.java"), entries.get(0).outputPath());
    }

    /**
     * 验证源码导出会保留 webapp 资源的模块相对路径。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldMapWebResourceToSourceDirectoryInSourceExportMode() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "cap-agent",
                Path.of("/project/cap-agent"),
                Path.of("/project/cap-agent/webapp/js/app.js"),
                Path.of("js/app.js").toString(),
                Path.of("webapp/js/app.js").toString(),
                SourceType.WEB_RESOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.SOURCE_EXPORT,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );

        List<ExportEntry> entries = patchLayoutService.plan(request, CompileResult.success(List.of(), Map.of(), "源码导出无需编译"), Map.of());

        // 源码模式必须复用原始资源路径，而不是继续裁掉 webapp 前缀。
        assertEquals(Path.of("/export/source/cap-agent/webapp/js/app.js"), entries.get(0).outputPath());
        assertEquals(Path.of("/project/cap-agent/webapp/js/app.js"), entries.get(0).sourcePath());
    }

    /**
     * 验证 webapp 资源会映射到 seeyon 目录。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldMapWebResourceToSeeyonDirectory() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "cap-agent",
                Path.of("/project/cap-agent"),
                Path.of("/project/cap-agent/webapp/js/app.js"),
                Path.of("js/app.js").toString(),
                Path.of("webapp/js/app.js").toString(),
                SourceType.WEB_RESOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(List.of("cap-agent"), Map.of());

        List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, Map.of());

        // webapp 资源必须映射到 /seeyon，不能混入 classes。
        assertEquals(Path.of("/export/seeyon/js/app.js"), entries.get(0).outputPath());
        assertEquals(Path.of("/project/cap-agent/webapp/js/app.js"), entries.get(0).sourcePath());
    }

    /**
     * 验证 bug jar 模式会映射到 seeyon/WEB-INF/lib/<jar名>.jar 下的包路径。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldMapBugJarToSeeyonWebInfLibJarDirectory() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "comi-biz",
                Path.of("/project/comi-biz"),
                Path.of("/project/comi-biz/src/main/java/demo/Test.java"),
                Path.of("demo/Test.class").toString(),
                Path.of("src/main/java/demo/Test.java").toString(),
                SourceType.JAVA_SOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.BUG_JAR,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(
                List.of("comi-biz"),
                Map.of("comi-biz", Path.of("/project/comi-biz/target/classes"))
        );
        Map<String, ModulePackagingInfo> packagingInfo = Map.of(
                "comi-biz",
                new ModulePackagingInfo("comi-biz", "seeyon-comi-biz", Path.of("/project/comi-biz/target/classes"))
        );

        List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, packagingInfo);

        // bug jar 模式必须落到 seeyon/WEB-INF/lib/<jar名>.jar 下，并直接保留类的包路径。
        assertEquals(Path.of("/export/seeyon/WEB-INF/lib/seeyon-comi-biz.jar/demo/Test.class"), entries.get(0).outputPath());
    }

    /**
     * 验证 bug jar 模式不允许导出 web 资源。
     */
    @Test
    void shouldRejectWebResourceWhenBugJarMode() {
        SelectedItem selectedItem = new SelectedItem(
                "cap-agent",
                Path.of("/project/cap-agent"),
                Path.of("/project/cap-agent/webapp/js/app.js"),
                Path.of("js/app.js").toString(),
                Path.of("webapp/js/app.js").toString(),
                SourceType.WEB_RESOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.BUG_JAR,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(List.of("cap-agent"), Map.of("cap-agent", Path.of("/project/cap-agent/target/classes")));
        Map<String, ModulePackagingInfo> packagingInfo = Map.of(
                "cap-agent",
                new ModulePackagingInfo("cap-agent", "cap-agent-jar", Path.of("/project/cap-agent/target/classes"))
        );

        // bug jar 只接受类文件，否则会生成错误的补丁结构。
        assertThrows(ExportException.class, () -> patchLayoutService.plan(request, compileResult, packagingInfo));
    }

    /**
     * 验证 IDEA 编译模式会使用真实输出目录，而不是固定 target/classes。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldUseIdeaCompilerOutputDirectory() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "comi-biz",
                Path.of("/project/comi-biz"),
                Path.of("/project/comi-biz/src/main/java/demo/Test.java"),
                Path.of("demo/Test.class").toString(),
                Path.of("src/main/java/demo/Test.java").toString(),
                SourceType.JAVA_SOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.IDEA,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(
                List.of("comi-biz"),
                Map.of("comi-biz", Path.of("/idea-out/production/comi-biz"))
        );

        List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, Map.of());

        // IDEA 编译模式必须使用 IDEA 的真实输出目录，否则会导出不到 class 文件。
        assertEquals(Path.of("/idea-out/production/comi-biz/demo/Test.class"), entries.get(0).sourcePath());
    }

    /**
     * 验证关闭编译后仍会使用现有 Maven 产物目录进行导出。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldUseExistingArtifactsWhenCompileDisabled() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "comi-biz",
                Path.of("/project/comi-biz"),
                Path.of("/project/comi-biz/src/main/java/demo/Test.java"),
                Path.of("demo/Test.class").toString(),
                Path.of("src/main/java/demo/Test.java").toString(),
                SourceType.JAVA_SOURCE
        );
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                true,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(
                List.of("comi-biz"),
                Map.of("comi-biz", Path.of("/project/comi-biz/target/classes")),
                "已跳过编译，直接使用现有产物"
        );

        List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, Map.of());

        // 关闭编译开关后应直接使用现有 Maven 产物，不再触发编译逻辑。
        assertEquals(Path.of("/project/comi-biz/target/classes/demo/Test.class"), entries.get(0).sourcePath());
    }
}
