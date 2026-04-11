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
 * Maven 打包元数据解析测试，验证 finalName 与 artifactId 的提取逻辑。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class PackagingMetadataServiceTest {

    private final PackagingMetadataService packagingMetadataService = new PackagingMetadataService();

    @TempDir
    Path tempDir;

    /**
     * 验证优先使用 finalName 作为 bug jar 目录名。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldPreferFinalNameFromPom() throws Exception {
        Path moduleDirectory = createModuleWithPom("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>demo-artifact</artifactId>
                    <build>
                        <finalName>demo-final-name</finalName>
                    </build>
                </project>
                """);
        SelectedItem selectedItem = buildSelectedItem(moduleDirectory);

        Map<String, ModulePackagingInfo> packagingInfo = packagingMetadataService.resolvePackaging(List.of(selectedItem));

        // 用户要求目录名与真实 jar 名一致，所以 finalName 必须优先级最高。
        assertEquals("demo-final-name", packagingInfo.get("demo-module").finalName());
    }

    /**
     * 验证 finalName 缺失时回退 artifactId。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldFallbackToArtifactIdWhenFinalNameMissing() throws Exception {
        Path moduleDirectory = createModuleWithPom("""
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>demo-artifact</artifactId>
                </project>
                """);
        SelectedItem selectedItem = buildSelectedItem(moduleDirectory);

        Map<String, ModulePackagingInfo> packagingInfo = packagingMetadataService.resolvePackaging(List.of(selectedItem));

        // 没有 finalName 时用 artifactId，至少保证 bug jar 模式仍可工作。
        assertEquals("demo-artifact", packagingInfo.get("demo-module").finalName());
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
        assertEquals("child-artifact", packagingInfo.get("demo-module").finalName());
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
                SourceType.JAVA_SOURCE
        );

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
