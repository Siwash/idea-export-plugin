package com.seeyon.ideaexport.service;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导出命令服务测试，验证主流程会在成功后回写历史路径。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
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
                Optional.of(new ExportRequest(ExportMode.STANDARD_PATCH, CompileMode.MAVEN_CURRENT_MODULE, false, tempDir.resolve("output"), List.of(selectedItem)))
        );

        exportCommandService.startExport((Project) null, (AnActionEvent) null);

        // 主流程成功后必须写回历史路径，否则跨项目共享历史无法成立。
        assertEquals(List.of(tempDir.resolve("output").toString()), recentPathStore.load());
    }

    /**
     * 测试专用导出命令服务，通过覆写对话框创建绕开 UI 依赖。
     */
    private static class TestExportCommandService extends ExportCommandService {

        private final Optional<ExportRequest> request;

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
         * 测试中同步执行导出，避免后台任务带来时序不稳定。
         *
         * @param project 当前项目
         * @param request 导出请求
         */
        @Override
        protected void dispatchExport(Project project, ExportRequest request) {
            executeExport(project, request);
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
                    Map.of("demo-module", Path.of("/idea-out/production/demo-module")),
                    request.skipCompile() ? "已跳过编译，直接使用现有产物" : "IDEA 编译成功"
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
            return List.of(new ExportEntry("demo-module", Path.of("/idea-out/production/demo-module/demo/Test.class"), outputPath, ExportEntryStatus.PENDING, ""));
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
        public ExportSummary export(List<ExportEntry> entries) {
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
     * 静默通知实现，避免测试触发真实 UI。
     */
    private static class SilentNotificationService extends NotificationService {

        /**
         * 测试中不展示任何对话框，避免引入 UI 依赖。
         */
        @Override
        public void notifyResult(Project project, ExportSummary summary, CompileResult compileResult, Path targetPath) {
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
