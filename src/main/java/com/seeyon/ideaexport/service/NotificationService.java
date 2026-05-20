package com.seeyon.ideaexport.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportResultViewModel;
import com.seeyon.ideaexport.model.ExportSummary;
import com.seeyon.ideaexport.ui.ExportResultDialog;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 导出结果通知服务，负责把执行结果以可读方式展示给用户。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class NotificationService {

    private final ResultActionService resultActionService;

    /**
     * 使用默认结果动作服务初始化通知服务。
     */
    public NotificationService() {
        this(new ResultActionService());
    }

    /**
     * 使用指定动作服务初始化通知服务，便于测试替换结果交互。
     *
     * @param resultActionService 结果动作服务
     */
    public NotificationService(ResultActionService resultActionService) {
        this.resultActionService = Objects.requireNonNull(resultActionService, "resultActionService cannot be null");
    }

    /**
     * 展示导出结果摘要。
     *
     * @param project 当前项目
     * @param summary 导出结果汇总
     * @param compileResult 编译结果
     * @param targetPath 导出目录
     * @param mode 导出模式
     */
    public void notifyResult(Project project, ExportSummary summary, CompileResult compileResult, Path targetPath, ExportMode mode) {
        notifyResult(project, summary, compileResult, targetPath, mode, new BufferedExportRuntimeReporter());
    }

    /**
     * 展示导出结果，并把过程摘要一起带入结果窗口。
     *
     * @param project 当前项目
     * @param summary 导出结果汇总
     * @param compileResult 编译结果
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @param reporter 过程上报器
     */
    public void notifyResult(Project project, ExportSummary summary, CompileResult compileResult, Path targetPath, ExportMode mode, BufferedExportRuntimeReporter reporter) {
        Objects.requireNonNull(summary, "summary cannot be null");
        Objects.requireNonNull(compileResult, "compileResult cannot be null");
        Objects.requireNonNull(targetPath, "targetPath cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        ExportResultViewModel viewModel = buildResultViewModel(summary, compileResult, targetPath, mode, reporter);
        ApplicationManager.getApplication().invokeLater(() -> new ExportResultDialog(project, viewModel, resultActionService).show());
    }

    /**
     * 展示失败结果窗口，保证异常场景也走统一结果主形态而不是退回简单消息框。
     *
     * @param project 当前项目
     * @param message 错误消息
     * @param targetPath 导出目录
     * @param mode 导出模式
     */
    public void notifyError(Project project, String message, Path targetPath, ExportMode mode) {
        notifyError(project, message, targetPath, mode, null, new BufferedExportRuntimeReporter());
    }

    /**
     * 展示失败结果窗口，并尽可能保留真实失败来源文件供后续定位。
     *
     * @param project 当前项目
     * @param message 错误消息
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @param sourcePath 失败来源文件
     */
    public void notifyError(Project project, String message, Path targetPath, ExportMode mode, Path sourcePath) {
        notifyError(project, message, targetPath, mode, sourcePath, -1, -1, new BufferedExportRuntimeReporter());
    }

    /**
     * 展示失败结果窗口，并尽可能保留真实失败来源文件和定位信息。
     *
     * @param project 当前项目
     * @param message 错误消息
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @param sourcePath 失败来源文件
     * @param line 失败行号
     * @param column 失败列号
     */
    public void notifyError(Project project, String message, Path targetPath, ExportMode mode, Path sourcePath, int line, int column) {
        notifyError(project, message, targetPath, mode, sourcePath, line, column, new BufferedExportRuntimeReporter());
    }

    /**
     * 展示失败结果窗口，并带入过程摘要。
     *
     * @param project 当前项目
     * @param message 错误消息
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @param sourcePath 失败来源文件
     * @param reporter 过程上报器
     */
    public void notifyError(Project project, String message, Path targetPath, ExportMode mode, Path sourcePath, BufferedExportRuntimeReporter reporter) {
        notifyError(project, message, targetPath, mode, sourcePath, -1, -1, reporter);
    }

    /**
     * 展示失败结果窗口，并带入过程摘要和精确定位信息。
     *
     * @param project 当前项目
     * @param message 错误消息
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @param sourcePath 失败来源文件
     * @param line 失败行号
     * @param column 失败列号
     * @param reporter 过程上报器
     */
    public void notifyError(Project project, String message, Path targetPath, ExportMode mode, Path sourcePath, int line, int column, BufferedExportRuntimeReporter reporter) {
        ExportEntry failedEntry = new ExportEntry("runtime", sourcePath, targetPath, ExportEntryStatus.FAILED, message, line, column);
        ExportSummary summary = new ExportSummary(List.of(failedEntry), 0, 1, 0);
        ExportResultViewModel viewModel = buildResultViewModel(summary, CompileResult.failure(message), targetPath, mode, reporter);
        ApplicationManager.getApplication().invokeLater(() -> new ExportResultDialog(project, viewModel, resultActionService).show());
    }

    /**
     * 兼容旧调用入口；没有目标路径时仍回退到错误框，避免完全丢失错误提示。
     *
     * @param project 当前项目
     * @param message 错误消息
     */
    public void notifyError(Project project, String message) {
        ExportEntry failedEntry = new ExportEntry("runtime", null, Path.of("."), ExportEntryStatus.FAILED, message, -1, -1);
        ExportSummary summary = new ExportSummary(List.of(failedEntry), 0, 1, 0);
        ExportResultViewModel viewModel = new ExportResultViewModel(null, message, "导出前校验失败", List.of(message), List.of(), List.of(failedEntry), List.of());
        // 没有目标目录的失败场景也必须统一进入结果窗口，避免体验层再次退回旧消息框。
        ApplicationManager.getApplication().invokeLater(() -> new ExportResultDialog(project, viewModel, resultActionService).show());
    }

    /**
     * 构建结果视图模型，统一把导出结果切分为成功/失败/跳过三类分组。
     *
     * @param summary 导出汇总
     * @param compileResult 编译结果
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @return 结果视图模型
     */
    ExportResultViewModel buildResultViewModel(ExportSummary summary, CompileResult compileResult, Path targetPath, ExportMode mode) {
        return buildResultViewModel(summary, compileResult, targetPath, mode, new BufferedExportRuntimeReporter());
    }

    /**
     * 构建结果视图模型，并把过程摘要一起带进结果窗口。
     *
     * @param summary 导出汇总
     * @param compileResult 编译结果
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @param reporter 过程上报器
     * @return 结果视图模型
     */
    ExportResultViewModel buildResultViewModel(ExportSummary summary, CompileResult compileResult, Path targetPath, ExportMode mode, BufferedExportRuntimeReporter reporter) {
        List<ExportEntry> successEntries = summary.entries().stream()
                .filter(entry -> entry.status() == ExportEntryStatus.EXPORTED)
                .toList();
        List<ExportEntry> failedEntries = summary.entries().stream()
                .filter(entry -> entry.status() == ExportEntryStatus.FAILED)
                .toList();
        List<ExportEntry> skippedEntries = summary.entries().stream()
                .filter(entry -> entry.status() == ExportEntryStatus.SKIPPED)
                .toList();
        Path outputRootPath = resolveOutputRootPath(targetPath, mode);
        return new ExportResultViewModel(outputRootPath, compileResult.summary(), reporter.getStage(), reporter.getLogLines(), successEntries, failedEntries, skippedEntries);
    }

    /**
     * 根据导出模式计算结果页应展示的根目录，避免源码模式仍错误显示 seeyon。
     *
     * @param targetPath 导出目录
     * @param mode 导出模式
     * @return 结果页输出根目录
     */
    private Path resolveOutputRootPath(Path targetPath, ExportMode mode) {
        return mode.isSourceExport() ? targetPath.resolve("source") : targetPath.resolve("seeyon");
    }

    /**
     * 统一生成明细文案，保证成功项也有可读输出。
     *
     * @param entry 导出项
     * @return 明细消息
     */
    private String resolveMessage(com.seeyon.ideaexport.model.ExportEntry entry) {
        if (Objects.nonNull(entry.message()) && !entry.message().isBlank()) {
            return entry.message();
        }
        return switch (entry.status()) {
            case EXPORTED -> "导出成功";
            case FAILED -> "导出失败";
            case SKIPPED -> "已跳过";
            case PENDING -> "待执行";
        };
    }
}
