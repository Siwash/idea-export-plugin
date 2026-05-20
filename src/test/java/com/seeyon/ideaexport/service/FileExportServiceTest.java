package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件导出执行测试，验证复制结果汇总和内部类补齐逻辑。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class FileExportServiceTest {

    private final FileExportService fileExportService = new FileExportService();

    @TempDir
    Path tempDir;

    /**
     * 验证普通文件复制成功后会生成成功汇总。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldCopySingleFileSuccessfully() throws Exception {
        Path sourceFile = createFile(tempDir.resolve("source/test.txt"), "hello");
        ExportEntry entry = ExportEntry.pending("demo", sourceFile, tempDir.resolve("output/test.txt"));

        ExportSummary summary = fileExportService.export(List.of(entry));

        // 成功复制后必须落盘目标文件，并在汇总中记录成功数量。
        assertEquals(1, summary.successCount());
        assertTrue(Files.exists(tempDir.resolve("output/test.txt")));
    }

    /**
     * 验证源码模式下的普通文本文件会按普通文件复制，不触发 class 相关补齐逻辑。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldCopySourceFileWithoutExpandingClassSiblings() throws Exception {
        Path sourceFile = createFile(tempDir.resolve("source/readme.md"), "hello-source");
        ExportEntry entry = ExportEntry.pending("demo", sourceFile, tempDir.resolve("output/source/demo/readme.md"));

        ExportSummary summary = fileExportService.export(List.of(entry));

        // 源码导出复制的是原始文件，不能因为同目录里有其他文件就错误触发 class 补齐逻辑。
        assertEquals(1, summary.successCount());
        assertEquals("hello-source", Files.readString(tempDir.resolve("output/source/demo/readme.md"), StandardCharsets.UTF_8));
    }

    /**
     * 验证 class 文件导出时会一并带出内部类文件。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldExportInnerClassFilesTogether() throws Exception {
        Path classesDirectory = tempDir.resolve("classes/demo");
        Path mainClass = createFile(classesDirectory.resolve("Demo.class"), "main");
        createFile(classesDirectory.resolve("Demo$Inner.class"), "inner");
        ExportEntry entry = ExportEntry.pending("demo", mainClass, tempDir.resolve("output/Demo.class"));

        ExportSummary summary = fileExportService.export(List.of(entry));

        // 内部类缺失会导致运行时缺类，所以 class 导出必须自动补齐同名前缀文件。
        assertEquals(2, summary.successCount());
        assertTrue(Files.exists(tempDir.resolve("output/Demo.class")));
        assertTrue(Files.exists(tempDir.resolve("output/Demo$Inner.class")));
    }

    /**
     * 创建测试文件并自动补齐父目录。
     *
     * @param filePath 文件路径
     * @param content 文件内容
     * @return 文件路径
     * @throws IOException 创建失败
     */
    private Path createFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        return filePath;
    }
}
