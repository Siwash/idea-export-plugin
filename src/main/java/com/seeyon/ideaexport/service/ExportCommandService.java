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
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "导出到 seeyon", false) {
            /**
             * 在后台执行实际导出流程。
             *
             * @param indicator 进度指示器
             */
            @Override
            public void run(ProgressIndicator indicator) {
                // 导出可能触发编译和批量复制，必须放到后台避免阻塞 IDE 前台交互。
                indicator.setText("正在导出到 seeyon");
                executeExport(project, request);
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
        try {
            CompileResult compileResult = compileService.compile(project, request);
            Map<String, ModulePackagingInfo> packagingInfo = request.mode() == ExportMode.BUG_JAR
                    ? packagingMetadataService.resolvePackaging(request.selectedItems())
                    : Map.of();
            List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, packagingInfo);
            ExportSummary exportSummary = fileExportService.export(entries);
            recentPathStore.record(request.targetPath().toString());
            notificationService.notifyResult(project, exportSummary, compileResult, request.targetPath());
        } catch (ExportException | IOException exception) {
            notificationService.notifyError(project, exception.getMessage());
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
}
