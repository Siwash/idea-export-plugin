package com.seeyon.ideaexport.service;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.ExportSummary;
import com.seeyon.ideaexport.model.ModulePackagingInfo;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.resolver.SelectionResolver;
import com.seeyon.ideaexport.storage.RecentPathStore;
import com.seeyon.ideaexport.ui.ExportDialog;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 导出命令主流程服务，统一编排选择解析、参数确认、编译、导出和结果提示。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class ExportCommandService {

    private final SelectionResolver selectionResolver;
    private final RecentPathStore recentPathStore;
    private final PackagingMetadataService packagingMetadataService;
    private final PatchLayoutService patchLayoutService;
    private final CompileService compileService;
    private final FileExportService fileExportService;
    private final NotificationService notificationService;

    /**
     * 使用默认依赖初始化导出命令服务。
     */
    public ExportCommandService() {
        this(new SelectionResolver(), new RecentPathStore(), new PackagingMetadataService(), new PatchLayoutService(), new CompileService(), new FileExportService(), new NotificationService());
    }

    /**
     * 使用指定依赖初始化导出命令服务，便于后续测试替换。
     *
     * @param selectionResolver 选择解析服务
     * @param recentPathStore 历史路径存储
     * @param packagingMetadataService 打包元数据服务
     * @param patchLayoutService 路径规划服务
     * @param compileService 编译服务
     * @param fileExportService 文件导出服务
     * @param notificationService 结果通知服务
     */
    public ExportCommandService(
            SelectionResolver selectionResolver,
            RecentPathStore recentPathStore,
            PackagingMetadataService packagingMetadataService,
            PatchLayoutService patchLayoutService,
            CompileService compileService,
            FileExportService fileExportService,
            NotificationService notificationService
    ) {
        this.selectionResolver = Objects.requireNonNull(selectionResolver, "selectionResolver cannot be null");
        this.recentPathStore = Objects.requireNonNull(recentPathStore, "recentPathStore cannot be null");
        this.packagingMetadataService = Objects.requireNonNull(packagingMetadataService, "packagingMetadataService cannot be null");
        this.patchLayoutService = Objects.requireNonNull(patchLayoutService, "patchLayoutService cannot be null");
        this.compileService = Objects.requireNonNull(compileService, "compileService cannot be null");
        this.fileExportService = Objects.requireNonNull(fileExportService, "fileExportService cannot be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService cannot be null");
    }

    /**
     * 启动导出主流程。
     *
     * @param project 当前项目
     * @param event Action 事件
     */
    public void startExport(Project project, AnActionEvent event) {
        try {
            List<SelectedItem> selectedItems = selectionResolver.resolve(project, event);
            List<String> recentPaths = recentPathStore.load();
            ExportRequest request = createExportRequest(project, selectedItems, recentPaths).orElse(null);
            if (Objects.isNull(request)) {
                return;
            }
            validateTargetPath(request);
            dispatchExport(project, request);
        } catch (ExportException | IOException exception) {
            notificationService.notifyError(project, exception.getMessage());
        }
    }

    /**
     * 校验导出目录可写；不存在时自动创建，满足空配置目录自动恢复的交互要求。
     *
     * @param request 导出请求
     * @throws ExportException 目录不可写时抛出
     */
    private void validateTargetPath(ExportRequest request) throws ExportException {
        try {
            Files.createDirectories(request.targetPath());
            if (!Files.isWritable(request.targetPath())) {
                // 目标目录已存在但不可写时必须在编译前终止，避免白跑一轮导出流程。
                throw new ExportException("导出路径不可写: " + request.targetPath());
            }
        } catch (IOException exception) {
            throw new ExportException("导出路径不可写: " + request.targetPath(), exception);
        }
    }

    /**
     * 在后台分发导出任务，避免在 Action 线程中执行编译和文件复制。
     *
     * @param project 当前项目
     * @param request 导出请求
     */
    protected void dispatchExport(Project project, ExportRequest request) {
        BufferedExportRuntimeReporter reporter = new BufferedExportRuntimeReporter();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "导出到 seeyon", false) {
            /**
             * 使用 IDEA 原生后台进度执行导出，避免自定义过程对话框与编译线程约束互相卡住。
             *
             * @param indicator 进度指示器
             */
            @Override
            public void run(ProgressIndicator indicator) {
                reporter.started("导出到 seeyon", 3);
                indicator.setText("准备开始导出...");
                executeExport(project, request, new ProgressAwareRuntimeReporter(reporter, indicator));
            }
        });
    }

    /**
     * 执行导出主流程，便于后台任务和测试场景复用同一条业务链路。
     *
     * @param project 当前项目
     * @param request 导出请求
     */
    protected void executeExport(Project project, ExportRequest request) {
        executeExport(project, request, new SilentExportRuntimeReporter());
    }

    /**
     * 执行带过程上报的导出主流程，便于过程窗口与测试场景共享同一套业务链路。
     *
     * @param project 当前项目
     * @param request 导出请求
     * @param reporter 过程上报器
     */
    protected void executeExport(Project project, ExportRequest request, ExportRuntimeReporter reporter) {
        try {
            reporter.updateStage("准备编译与导出任务...");
            reporter.updateProgress(0.1D);
            CompileResult compileResult = compileService.compile(project, request, reporter);
            Map<String, ModulePackagingInfo> packagingInfo = request.mode() == ExportMode.BUG_JAR
                    ? packagingMetadataService.resolvePackaging(request.selectedItems())
                    : Map.of();
            reporter.updateStage("正在规划导出路径...");
            reporter.updateProgress(0.45D);
            List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, packagingInfo);
            reporter.updateStage("正在复制导出文件...");
            reporter.updateProgress(0.7D);
            ExportSummary exportSummary = fileExportService.export(entries, reporter);
            recentPathStore.record(request.targetPath().toString());
            reporter.updateProgress(1D);
            reporter.finished();
            notificationService.notifyResult(project, exportSummary, compileResult, request.targetPath(), request.mode(), resolveBufferedReporter(reporter));
        } catch (ExportException | IOException exception) {
            reporter.appendLog(exception.getMessage());
            reporter.finished();
            if (exception instanceof ExportException exportException && exportException.getSourcePath() != null) {
                notificationService.notifyError(
                        project,
                        exception.getMessage(),
                        request.targetPath(),
                        request.mode(),
                        exportException.getSourcePath(),
                        exportException.getLine(),
                        exportException.getColumn(),
                        resolveBufferedReporter(reporter)
                );
                return;
            }
            notificationService.notifyError(project, exception.getMessage(), request.targetPath(), request.mode(), null, resolveBufferedReporter(reporter));
        }
    }

    /**
     * 创建导出请求；默认使用真实对话框，测试场景可覆写为固定请求。
     *
     * @param project 当前项目
     * @param selectedItems 选中项
     * @param recentPaths 历史路径
     * @return 导出请求
     */
    protected java.util.Optional<ExportRequest> createExportRequest(Project project, List<SelectedItem> selectedItems, List<String> recentPaths) {
        ExportDialog exportDialog = new ExportDialog(project, selectedItems, recentPaths);
        return exportDialog.showAndGetRequest();
    }

    /**
     * 把通用上报器还原成缓冲上报器，供结果窗口读取过程摘要。
     *
     * @param reporter 通用上报器
     * @return 缓冲上报器
     */
    private BufferedExportRuntimeReporter resolveBufferedReporter(ExportRuntimeReporter reporter) {
        if (reporter instanceof ProgressAwareRuntimeReporter progressAwareRuntimeReporter) {
            return progressAwareRuntimeReporter.getDelegate();
        }
        if (reporter instanceof BufferedExportRuntimeReporter bufferedExportRuntimeReporter) {
            return bufferedExportRuntimeReporter;
        }
        return new BufferedExportRuntimeReporter();
    }
}
