package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import com.seeyon.ideaexport.model.ExportSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出通知测试，验证输出位置与成功/失败/跳过明细都会展示。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class NotificationServiceTest {

    private final NotificationService notificationService = new NotificationService();

    /**
     * 验证结果摘要会展示输出位置。
     */
    @Test
    void shouldContainTargetPathInSummaryMessage() {
        Path targetPath = Path.of("/export/path");
        ExportSummary summary = new ExportSummary(List.of(), 1, 0, 0);
        CompileResult compileResult = CompileResult.success(List.of("demo"), Map.of(), "编译成功");

        String message = notificationService.buildSummaryMessage(summary, compileResult, targetPath);

        // 输出位置是验收硬要求，结果摘要必须直接展示。
        assertTrue(message.contains("输出位置:"));
        assertTrue(message.contains(targetPath.toString()));
    }

    /**
     * 验证部分成功部分失败时会同时列出成功项、失败项和跳过项。
     */
    @Test
    void shouldContainSuccessFailureAndSkippedEntries() {
        ExportEntry successEntry = new ExportEntry("demo", Path.of("/src/A.class"), Path.of("/export/A.class"), ExportEntryStatus.EXPORTED, "导出成功");
        ExportEntry failedEntry = new ExportEntry("demo", Path.of("/src/B.class"), Path.of("/export/B.class"), ExportEntryStatus.FAILED, "源文件不存在");
        ExportEntry skippedEntry = new ExportEntry("demo", Path.of("/src/C.txt"), Path.of("/export/C.txt"), ExportEntryStatus.SKIPPED, "不支持的文件类型");
        ExportSummary summary = new ExportSummary(List.of(successEntry, failedEntry, skippedEntry), 1, 1, 1);
        CompileResult compileResult = CompileResult.success(List.of("demo"), Map.of(), "编译成功");

        String message = notificationService.buildSummaryMessage(summary, compileResult, Path.of("/export"));

        // 部分成功部分失败时必须同时列出成功项、失败项和跳过项，不能只列失败。
        assertTrue(message.contains("EXPORTED"));
        assertTrue(message.contains(successEntry.outputPath().toString()));
        assertTrue(message.contains("导出成功"));
        assertTrue(message.contains("FAILED"));
        assertTrue(message.contains(failedEntry.outputPath().toString()));
        assertTrue(message.contains("源文件不存在"));
        assertTrue(message.contains("SKIPPED"));
        assertTrue(message.contains(skippedEntry.outputPath().toString()));
        assertTrue(message.contains("不支持的文件类型"));
    }
}
