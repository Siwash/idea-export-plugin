package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导出通知测试，验证输出位置、过程日志与失败定位信息都会进入结果模型。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
class NotificationServiceTest {

    private final NotificationService notificationService = new NotificationService();

    /**
     * 验证补丁模式结果视图模型会聚合到 seeyon 根目录。
     */
    @Test
    void shouldContainSeeyonRootPathInResultViewModel() {
        Path targetPath = Path.of("/export/path");
        ExportSummary summary = new ExportSummary(List.of(), 1, 0, 0);
        CompileResult compileResult = CompileResult.success(List.of("demo"), Map.of(), "编译成功");

        var viewModel = notificationService.buildResultViewModel(summary, compileResult, targetPath, ExportMode.STANDARD_PATCH);

        // 补丁模式结果窗口展示路径要聚合到 seeyon 根目录，避免用户阅读每条结果时重复看长路径。
        assertEquals(Path.of("/export/path/seeyon"), viewModel.outputRootPath());
    }

    /**
     * 验证源码模式结果视图模型会聚合到 source 根目录。
     */
    @Test
    void shouldContainSourceRootPathInResultViewModelForSourceExport() {
        Path targetPath = Path.of("/export/path");
        ExportSummary summary = new ExportSummary(List.of(), 1, 0, 0);
        CompileResult compileResult = CompileResult.success(List.of(), Map.of(), "源码导出无需编译");

        var viewModel = notificationService.buildResultViewModel(summary, compileResult, targetPath, ExportMode.SOURCE_EXPORT);

        // 源码模式必须展示 source 根目录，否则结果页会把用户带到错误的补丁目录。
        assertEquals(Path.of("/export/path/source"), viewModel.outputRootPath());
    }

    /**
     * 验证部分成功部分失败时会同时列出成功项、失败项和跳过项。
     */
    @Test
    void shouldContainSuccessFailureAndSkippedEntries() {
        ExportEntry successEntry = new ExportEntry("demo", Path.of("/src/A.class"), Path.of("/export/A.class"), ExportEntryStatus.EXPORTED, "导出成功", -1, -1);
        ExportEntry failedEntry = new ExportEntry("demo", Path.of("/src/B.class"), Path.of("/export/B.class"), ExportEntryStatus.FAILED, "源文件不存在", -1, -1);
        ExportEntry skippedEntry = new ExportEntry("demo", Path.of("/src/C.txt"), Path.of("/export/C.txt"), ExportEntryStatus.SKIPPED, "不支持的文件类型", -1, -1);
        ExportSummary summary = new ExportSummary(List.of(successEntry, failedEntry, skippedEntry), 1, 1, 1);
        CompileResult compileResult = CompileResult.success(List.of("demo"), Map.of(), "编译成功");

        var viewModel = notificationService.buildResultViewModel(summary, compileResult, Path.of("/export"), ExportMode.STANDARD_PATCH);

        // 部分成功部分失败时必须在结果模型里拆出三类分组，供独立结果窗口聚合展示。
        assertEquals(1, viewModel.successEntries().size());
        assertEquals(successEntry.outputPath(), viewModel.successEntries().get(0).outputPath());
        assertEquals(1, viewModel.failedEntries().size());
        assertEquals(failedEntry.outputPath(), viewModel.failedEntries().get(0).outputPath());
        assertEquals(1, viewModel.skippedEntries().size());
        assertEquals(skippedEntry.outputPath(), viewModel.skippedEntries().get(0).outputPath());
    }

    /**
     * 验证过程日志与阶段文本会进入结果视图模型，供结果页控制台展示。
     */
    @Test
    void shouldCarryProcessLogsAndStageIntoResultViewModel() {
        BufferedExportRuntimeReporter reporter = new BufferedExportRuntimeReporter();
        reporter.started("导出到 seeyon", 3);
        reporter.updateStage("正在执行 Maven 串行安装...");
        reporter.appendLog("[module-a] /project/module-a/src/main/java/demo/Test.java:[30,20] 需要 ')'");

        ExportSummary summary = new ExportSummary(List.of(), 0, 0, 0);
        CompileResult compileResult = CompileResult.success(List.of("module-a"), Map.of(), "编译成功");

        var viewModel = notificationService.buildResultViewModel(summary, compileResult, Path.of("/export"), ExportMode.STANDARD_PATCH, reporter);

        // 结果页控制台依赖 processLogs 与 processStage，不能在通知层丢掉这些过程信息。
        assertEquals("正在执行 Maven 串行安装...", viewModel.processStage());
        assertEquals(2, viewModel.processLogs().size());
        assertEquals("[module-a] /project/module-a/src/main/java/demo/Test.java:[30,20] 需要 ')'", viewModel.processLogs().get(1));
    }

    /**
     * 验证失败项会保留 sourcePath、line、column，供结果列表和控制台统一定位。
     */
    @Test
    void shouldKeepFailureLocationMetadataInViewModel() {
        ExportEntry failedEntry = new ExportEntry(
                "demo",
                Path.of("/src/Test.java"),
                Path.of("/export/Test.class"),
                ExportEntryStatus.FAILED,
                "cannot find symbol",
                12,
                3
        );
        ExportSummary summary = new ExportSummary(List.of(failedEntry), 0, 1, 0);
        CompileResult compileResult = CompileResult.failure("编译失败");

        var viewModel = notificationService.buildResultViewModel(summary, compileResult, Path.of("/export"), ExportMode.STANDARD_PATCH);

        // 失败定位元数据必须完整保留，结果页双击定位和控制台点击定位都依赖这组字段。
        assertEquals(Path.of("/src/Test.java"), viewModel.failedEntries().get(0).sourcePath());
        assertEquals(12, viewModel.failedEntries().get(0).line());
        assertEquals(3, viewModel.failedEntries().get(0).column());
    }
}
