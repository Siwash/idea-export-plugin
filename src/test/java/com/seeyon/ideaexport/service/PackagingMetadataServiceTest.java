package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.ModulePackagingInfo;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Maven 打包元数据解析测试，验证 artifactId 的提取与 seeyon jar 命名逻辑。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class PackagingMetadataServiceTest {

    private final PackagingMetadataService packagingMetadataService = new PackagingMetadataService();

    @TempDir
    Path tempDir;

    /**
     * 验证 bug jar 始终使用 artifactId，并补齐 seeyon 前缀和 .jar 后缀。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldUseArtifactIdWithSeeyonJarName() throws Exception {
        Path moduleDirectory = createModuleWithPom("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>ctp-workflow-component</artifactId>
                    <build>
                        <finalName>wrong-final-name</finalName>
                    </build>
                </project>
                """);
        SelectedItem selectedItem = buildSelectedItem(moduleDirectory);

        Map<String, ModulePackagingInfo> packagingInfo = packagingMetadataService.resolvePackaging(List.of(selectedItem));

        // bug jar 规范要求读取当前工程 artifactId，并统一生成 seeyon-<artifactId>.jar。
        assertEquals("ctp-workflow-component", packagingInfo.get("demo-module").artifactId());
        assertEquals("seeyon-ctp-workflow-component.jar", packagingInfo.get("demo-module").jarDirectoryName());
    }

    /**
     * 验证已带 seeyon 前缀的 artifactId 不会重复添加前缀。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldNotDuplicateSeeyonPrefix() throws Exception {
        Path moduleDirectory = createModuleWithPom("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>seeyon-cap-agent</artifactId>
                </project>
                """);
        SelectedItem selectedItem = buildSelectedItem(moduleDirectory);

        Map<String, ModulePackagingInfo> packagingInfo = packagingMetadataService.resolvePackaging(List.of(selectedItem));

        // artifactId 已符合 seeyon 命名时只补 jar 后缀，避免生成 seeyon-seeyon-*。
        assertEquals("seeyon-cap-agent.jar", packagingInfo.get("demo-module").jarDirectoryName());
    }

    /**
     * 验证不会误取 parent 节点里的 artifactId。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldIgnoreParentArtifactId() throws Exception {
        Path moduleDirectory = createModuleWithPom("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>demo</groupId>
                        <artifactId>parent-artifact</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>child-artifact</artifactId>
                </project>
                """);
        SelectedItem selectedItem = buildSelectedItem(moduleDirectory);

        Map<String, ModulePackagingInfo> packagingInfo = packagingMetadataService.resolvePackaging(List.of(selectedItem));

        // 目录名必须来自当前模块，而不是 parent。
        assertEquals("child-artifact", packagingInfo.get("demo-module").artifactId());
        assertEquals("seeyon-child-artifact.jar", packagingInfo.get("demo-module").jarDirectoryName());
    }

    /**
     * 验证 pom 缺失时直接失败，避免错误目录结构继续向下传播。
     */
    @Test
    void shouldFailWhenPomMissing() {
        SelectedItem selectedItem = new SelectedItem(
                "demo-module",
                tempDir.resolve("missing-module"),
                tempDir.resolve("missing-module/src/main/java/demo/Test.java"),
                "demo/Test.class",
                "src/main/java/demo/Test.java",
                SourceType.JAVA_SOURCE
        );

        assertThrows(ExportException.class, () -> packagingMetadataService.resolvePackaging(List.of(selectedItem)));
    }

    /**
     * 验证 artifactId 缺失时直接失败，避免生成错误 jar 名。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldFailWhenArtifactIdMissing() throws Exception {
        Path moduleDirectory = createModuleWithPom("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <build>
                        <finalName>wrong-final-name</finalName>
                    </build>
                </project>
                """);
        SelectedItem selectedItem = buildSelectedItem(moduleDirectory);

        assertThrows(ExportException.class, () -> packagingMetadataService.resolvePackaging(List.of(selectedItem)));
    }

    /**
     * 构造默认选中项，减少重复样板代码。
     *
     * @param moduleDirectory 模块目录
     * @return 选中项
     */
    private SelectedItem buildSelectedItem(Path moduleDirectory) {
        return new SelectedItem(
                "demo-module",
                moduleDirectory,
                moduleDirectory.resolve("src/main/java/demo/Test.java"),
                "demo/Test.class",
                "src/main/java/demo/Test.java",
                SourceType.JAVA_SOURCE
        );
    }

    /**
     * 创建带 pom 的临时模块目录，便于测试不同元数据组合。
     *
     * @param pomContent pom 内容
     * @return 模块目录
     * @throws IOException 文件创建失败
     */
    private Path createModuleWithPom(String pomContent) throws IOException {
        Path moduleDirectory = tempDir.resolve("module-" + System.nanoTime());
        Files.createDirectories(moduleDirectory.resolve("src/main/java/demo"));
        Files.writeString(moduleDirectory.resolve("pom.xml"), pomContent, StandardCharsets.UTF_8);
        return moduleDirectory;
    }
}
