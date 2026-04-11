package com.seeyon.ideaexport.resolver;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.SourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 选择解析规则测试，验证文件类型识别、相对路径映射和显式选择优先级逻辑。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class SelectionResolverTest {

    private final SelectionResolver selectionResolver = new SelectionResolver();

    /**
     * 验证 Java 源码会识别为类文件导出类型。
     */
    @Test
    void shouldDetectJavaSourceType() {
        assertEquals(SourceType.JAVA_SOURCE, selectionResolver.detectSourceType("/project/demo/src/main/java/com/seeyon/Test.java"));
    }

    /**
     * 验证 webapp 资源会识别为静态资源导出类型。
     */
    @Test
    void shouldDetectWebResourceType() {
        assertEquals(SourceType.WEB_RESOURCE, selectionResolver.detectSourceType("/project/demo/webapp/js/app.js"));
    }

    /**
     * 验证 Java 源码相对路径会转换成 class 产物路径。
     *
     * @throws ExportException 路径转换失败
     */
    @Test
    void shouldBuildJavaRelativeOutputPath() throws ExportException {
        String relativePath = selectionResolver.buildRelativeOutputPath(
                "/project/demo/src/main/java/com/seeyon/Test.java",
                "Test.java",
                SourceType.JAVA_SOURCE
        );

        // Java 源码必须转成 .class，相对路径才可直接拼接到 classes 目录。
        assertEquals("com/seeyon/Test.class", relativePath);
    }

    /**
     * 验证 webapp 资源会保留 webapp 之后的相对路径。
     *
     * @throws ExportException 路径转换失败
     */
    @Test
    void shouldBuildWebRelativeOutputPath() throws ExportException {
        String relativePath = selectionResolver.buildRelativeOutputPath(
                "/project/demo/webapp/js/app.js",
                "app.js",
                SourceType.WEB_RESOURCE
        );

        // web 资源只保留 webapp 之后的路径，导出时统一挂到 /seeyon。
        assertEquals("js/app.js", relativePath);
    }

    /**
     * 验证不支持文件类型会保留文件名，便于后续结果汇总提示被跳过项。
     *
     * @throws ExportException 路径转换失败
     */
    @Test
    void shouldKeepFileNameForUnsupportedType() throws ExportException {
        String relativePath = selectionResolver.buildRelativeOutputPath(
                "/project/demo/readme.md",
                "readme.md",
                SourceType.UNSUPPORTED
        );

        // 不支持类型也要保留文件名，否则结果面板无法准确提示被跳过项。
        assertEquals("readme.md", relativePath);
    }

    /**
     * 验证存在显式选中 changes 时，不会混入通用文件选择。
     */
    @Test
    void shouldPreferSelectedChangesOverCommonFiles() {
        VirtualFile selectedFile = new LightVirtualFile("Selected.java", PlainTextFileType.INSTANCE, "selected");
        VirtualFile unrelatedFile = new LightVirtualFile("Unrelated.java", PlainTextFileType.INSTANCE, "unrelated");

        List<VirtualFile> virtualFiles = selectionResolver.chooseVirtualFiles(
                new VirtualFile[]{unrelatedFile},
                null,
                List.of(selectedFile)
        );

        // Git 变更列表必须只认显式选中的变更，不能把通用文件选择混进来。
        assertEquals(List.of(selectedFile), virtualFiles);
    }

    /**
     * 验证没有显式选中 changes 时，才回退到通用文件选择。
     */
    @Test
    void shouldFallbackToCommonFilesWhenSelectedChangesMissing() {
        VirtualFile arrayFile = new LightVirtualFile("Array.java", PlainTextFileType.INSTANCE, "array");
        VirtualFile singleFile = new LightVirtualFile("Single.java", PlainTextFileType.INSTANCE, "single");

        List<VirtualFile> virtualFiles = selectionResolver.chooseVirtualFiles(
                new VirtualFile[]{arrayFile},
                singleFile,
                List.of()
        );

        // 只有在没有显式选中 changes 时，通用文件选择才参与导出。
        assertEquals(List.of(arrayFile, singleFile), virtualFiles);
    }
}
