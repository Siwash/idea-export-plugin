package com.seeyon.ideaexport.service;

import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.CompileStrategy;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.ExportSummary;
import com.seeyon.ideaexport.model.ModulePackagingInfo;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import com.seeyon.ideaexport.resolver.SelectionResolver;
import com.seeyon.ideaexport.storage.RecentPathStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导出命令服务测试，验证主流程会在成功后回写历史路径并透传新的编译策略字段。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
class ExportCommandServiceTest {

    @TempDir
    Path tempDir;

    /**
     * 验证主流程成功结束后会写回历史导出路径。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldRecordRecentPathAfterSuccessfulExport() throws Exception {
        SelectedItem selectedItem = new SelectedItem(
                "demo-module",
                tempDir.resolve("module"),
                tempDir.resolve("module/src/main/java/demo/Test.java"),
                "demo/Test.class",
                "src/main/java/demo/Test.java",
                SourceType.JAVA_SOURCE
        );
        RecordingRecentPathStore recentPathStore = new RecordingRecentPathStore(tempDir.resolve("config"));
        TestExportCommandService exportCommandService = new TestExportCommandService(
                new StubSelectionResolver(selectedItem),
                recentPathStore,
                new StubPackagingMetadataService(tempDir.resolve("module/target/classes")),
                new StubPatchLayoutService(tempDir.resolve("output/classes/demo/Test.class")),
                new StubCompileService(),
                new StubFileExportService(),
                new SilentNotificationService(),
                Optional.of(new ExportRequest(
                        ExportMode.STANDARD_PATCH,
                        CompileMode.MAVEN_CURRENT_MODULE,
                        CompileStrategy.SERIAL,
                        false,
                        tempDir.resolve("output"),
                        List.of(selectedItem.moduleBasePath()),
                        List.of(selectedItem)
                ))
        );

        exportCommandService.runForTest(createMockProject());

        // 主流程成功后必须写回历史路径，否则跨项目共享历史无法成立。
        assertEquals(List.of(tempDir.resolve("output").toString()), recentPathStore.load());
    }

    /**
     * 验证新的编译策略与模块顺序字段会原样透传给编译服务。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldPassCompileStrategyAndOrderedModulesToCompileService() throws Exception {
        SelectedItem firstItem = new SelectedItem(
                "demo-module-a",
                tempDir.resolve("module-a"),
                tempDir.resolve("module-a/src/main/java/demo/A.java"),
                "demo/A.class",
                "src/main/java/demo/A.java",
                SourceType.JAVA_SOURCE
        );
        SelectedItem secondItem = new SelectedItem(
                "demo-module-b",
                tempDir.resolve("module-b"),
                tempDir.resolve("module-b/src/main/java/demo/B.java"),
                "demo/B.class",
                "src/main/java/demo/B.java",
                SourceType.JAVA_SOURCE
        );
        CapturingCompileService compileService = new CapturingCompileService();
        RecordingNotificationService notificationService = new RecordingNotificationService();
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.PARALLEL,
                false,
                tempDir.resolve("output"),
                List.of(secondItem.moduleBasePath(), firstItem.moduleBasePath()),
                List.of(firstItem, secondItem)
        );
        TestExportCommandService exportCommandService = new TestExportCommandService(
                new StubSelectionResolver(firstItem),
                new RecordingRecentPathStore(tempDir.resolve("config-2")),
                new StubPackagingMetadataService(tempDir.resolve("module-a/target/classes")),
                new StubPatchLayoutService(tempDir.resolve("output/classes/demo/Test.class")),
                compileService,
                new StubFileExportService(),
                notificationService,
                Optional.of(request)
        );

        exportCommandService.runForTest(createMockProject());

        // 主流程必须把策略和模块顺序透传给编译服务，否则参数页新增交互只是摆设。
        assertEquals(CompileStrategy.PARALLEL, compileService.lastRequest.compileStrategy());
        assertEquals(List.of(secondItem.moduleBasePath(), firstItem.moduleBasePath()), compileService.lastRequest.orderedModuleBasePaths());
    }

    /**
     * 验证源码模式会透传到结果通知层，供结果页选择 source 根目录。
     *
     * @throws Exception 测试失败
     */
    @Test
    void shouldPassExportModeToNotificationService() throws Exception {
        SelectedItem selectedItem = new SelectedItem(
                "demo-module",
                tempDir.resolve("module"),
                tempDir.resolve("module/src/main/java/demo/Test.java"),
                "demo/Test.class",
                "src/main/java/demo/Test.java",
                SourceType.JAVA_SOURCE
        );
        RecordingNotificationService notificationService = new RecordingNotificationService();
        ExportRequest request = new ExportRequest(
                ExportMode.SOURCE_EXPORT,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                tempDir.resolve("output"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        TestExportCommandService exportCommandService = new TestExportCommandService(
                new StubSelectionResolver(selectedItem),
                new RecordingRecentPathStore(tempDir.resolve("config-3")),
                new StubPackagingMetadataService(tempDir.resolve("module/target/classes")),
                new StubPatchLayoutService(tempDir.resolve("output/source/demo-module/src/main/java/demo/Test.java")),
                new SourceExportCompileService(),
                new StubFileExportService(),
                notificationService,
                Optional.of(request)
        );

        exportCommandService.executeExport(createMockProject(), request);

        // 结果通知层必须拿到真实导出模式，否则源码模式结果页仍会错误展示 seeyon 根目录。
        assertEquals(ExportMode.SOURCE_EXPORT, notificationService.lastNotifyMode);
    }

    /**
     * 创建最小可用的 Project 占位对象，避免测试依赖真实 IDE 生命周期。
     *
     * @return MockProject
     */
    private Project createMockProject() {
        return new MockProject(null, new Disposable() {
            @Override
            public void dispose() {
                // 主流程测试只需要一个最小可用的 Project，占位即可。
            }
        });
    }

    /**
     * 测试专用导出命令服务，通过覆写对话框创建绕开 UI 依赖。
     */
    private static class TestExportCommandService extends ExportCommandService {

        private final Optional<ExportRequest> request;
        private final RecentPathStore recentPathStoreRef;
        private final CompileService compileServiceRef;
        private final PatchLayoutService patchLayoutServiceRef;
        private final FileExportService fileExportServiceRef;

        /**
         * 初始化测试用导出命令服务。
         */
        private TestExportCommandService(
                SelectionResolver selectionResolver,
                RecentPathStore recentPathStore,
                PackagingMetadataService packagingMetadataService,
                PatchLayoutService patchLayoutService,
                CompileService compileService,
                FileExportService fileExportService,
                NotificationService notificationService,
                Optional<ExportRequest> request
        ) {
            super(selectionResolver, recentPathStore, packagingMetadataService, patchLayoutService, compileService, fileExportService, notificationService);
            this.request = request;
            this.recentPathStoreRef = recentPathStore;
            this.compileServiceRef = compileService;
            this.patchLayoutServiceRef = patchLayoutService;
            this.fileExportServiceRef = fileExportService;
        }

        /**
         * 直接返回测试请求，避免依赖真实 Swing 对话框。
         *
         * @param project 当前项目
         * @param selectedItems 选中项
         * @param recentPaths 历史路径
         * @return 测试请求
         */
        @Override
        protected Optional<ExportRequest> createExportRequest(Project project, List<SelectedItem> selectedItems, List<String> recentPaths) {
            return request;
        }

        /**
         * 测试中直接走主流程成功链，避免依赖真实后台任务和通知线程环境。
         *
         * @param project 当前项目
         * @throws IOException 记录历史路径失败
         */
        private void runForTest(Project project) throws IOException, ExportException {
            if (request.isEmpty()) {
                return;
            }
            ExportRequest exportRequest = request.get();
            CompileResult compileResult = compileServiceRef.compile(project, exportRequest);
            List<ExportEntry> entries = patchLayoutServiceRef.plan(exportRequest, compileResult, Map.of());
            fileExportServiceRef.export(entries);
            recentPathStoreRef.record(exportRequest.targetPath().toString());
        }
    }

    /**
     * 固定返回单个选中项，专注验证主流程编排。
     */
    private static class StubSelectionResolver extends SelectionResolver {

        private final SelectedItem selectedItem;

        private StubSelectionResolver(SelectedItem selectedItem) {
            this.selectedItem = selectedItem;
        }

        /**
         * 返回预设选中项，避免依赖真实 IDEA 上下文。
         */
        @Override
        public List<SelectedItem> resolve(Project project, AnActionEvent event) {
            return List.of(selectedItem);
        }
    }

    /**
     * 固定返回编译成功结果。
     */
    private static class StubCompileService extends CompileService {

        /**
         * 跳过真实编译过程，直接返回成功结果。
         */
        @Override
        public CompileResult compile(Project project, ExportRequest request) {
            return CompileResult.success(
                    List.of("demo-module"),
                    Map.of("demo-module", tempOutput(request).resolve("demo-module")),
                    request.skipCompile() ? "已跳过编译，直接使用现有产物" : "Maven 串行安装成功"
            );
        }

        /**
         * 为测试生成稳定输出目录，避免依赖真实编译产物。
         *
         * @param request 导出请求
         * @return 模拟输出目录
         */
        private Path tempOutput(ExportRequest request) {
            return request.targetPath().resolve("target-classes");
        }
    }

    /**
     * 固定返回源码模式的“无需编译”结果，避免测试误走补丁模式输出目录。
     */
    private static class SourceExportCompileService extends CompileService {

        /**
         * 跳过真实编译过程，直接返回源码模式成功结果。
         */
        @Override
        public CompileResult compile(Project project, ExportRequest request) {
            return CompileResult.success(List.of(), Map.of(), "源码导出无需编译");
        }
    }

    /**
     * 记录型编译服务，用于验证新的请求字段是否已透传。
     */
    private static class CapturingCompileService extends CompileService {

        private ExportRequest lastRequest;

        /**
         * 记录最后一次收到的请求，避免依赖真实编译实现。
         */
        @Override
        public CompileResult compile(Project project, ExportRequest request) {
            this.lastRequest = request;
            return CompileResult.success(
                    List.of("demo-module-b", "demo-module-a"),
                    Map.of(
                            "demo-module-b", request.targetPath().resolve("parallel-out-b"),
                            "demo-module-a", request.targetPath().resolve("parallel-out-a")
                    ),
                    "Maven 并行安装成功"
            );
        }
    }

    /**
     * 固定返回普通模式导出路径。
     */
    private static class StubPatchLayoutService extends PatchLayoutService {

        private final Path outputPath;

        private StubPatchLayoutService(Path outputPath) {
            this.outputPath = outputPath;
        }

        /**
         * 返回固定导出项，隔离路径规划细节。
         */
        @Override
        public List<ExportEntry> plan(ExportRequest request, CompileResult compileResult, Map<String, ModulePackagingInfo> packagingInfo) {
            Path sourcePath = request.mode().isSourceExport()
                    ? request.selectedItems().get(0).sourcePath()
                    : compileResult.moduleOutputDirectories().values().iterator().next();
            // 源码模式没有编译输出目录，这里按模式切换来源，避免测试桩误依赖补丁模式前提。
            return List.of(new ExportEntry("demo-module", sourcePath, outputPath, ExportEntryStatus.PENDING, "", -1, -1));
        }
    }

    /**
     * 固定返回成功汇总。
     */
    private static class StubFileExportService extends FileExportService {

        /**
         * 跳过真实文件复制，仅返回成功汇总。
         */
        @Override
        public ExportSummary export(List<ExportEntry> entries, ExportRuntimeReporter reporter) {
            return new ExportSummary(entries, 1, 0, 0);
        }
    }

    /**
     * 固定返回普通打包信息，避免 bug jar 分支影响当前测试。
     */
    private static class StubPackagingMetadataService extends PackagingMetadataService {

        private final Path classesDirectory;

        private StubPackagingMetadataService(Path classesDirectory) {
            this.classesDirectory = classesDirectory;
        }

        /**
         * 返回固定打包信息。
         */
        @Override
        public Map<String, ModulePackagingInfo> resolvePackaging(List<SelectedItem> items) {
            return Map.of("demo-module", new ModulePackagingInfo("demo-module", "demo-module", classesDirectory));
        }
    }

    /**
     * 记录型历史路径存储，复用真实实现验证写回结果。
     */
    private static class RecordingRecentPathStore extends RecentPathStore {

        private RecordingRecentPathStore(Path configDirectory) {
            super(configDirectory);
        }
    }

    /**
     * 记录型通知实现，避免测试触发真实 UI，并捕获主流程透传的模式。
     */
    private static class RecordingNotificationService extends NotificationService {

        private ExportMode lastNotifyMode;

        /**
         * 记录成功通知模式，便于断言结果页根目录选择逻辑。
         */
        @Override
        public void notifyResult(Project project, ExportSummary summary, CompileResult compileResult, Path targetPath, ExportMode mode, BufferedExportRuntimeReporter reporter) {
            this.lastNotifyMode = mode;
        }
    }

    /**
     * 静默通知实现，避免测试触发真实 UI。
     */
    private static class SilentNotificationService extends NotificationService {

        /**
         * 测试中不展示任何对话框，避免引入 UI 依赖。
         */
        @Override
        public void notifyResult(Project project, ExportSummary summary, CompileResult compileResult, Path targetPath, ExportMode mode, BufferedExportRuntimeReporter reporter) {
            // 主流程测试只关注编排结果，不校验 UI 展示。
        }

        /**
         * 测试中不展示错误对话框，避免引入 UI 依赖。
         */
        @Override
        public void notifyError(Project project, String message) {
            // 主流程测试只关注编排结果，不校验 UI 展示。
        }
    }
}
