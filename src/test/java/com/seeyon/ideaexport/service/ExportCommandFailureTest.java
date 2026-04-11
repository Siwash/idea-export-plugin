package com.seeyon.ideaexport.service;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.ExportSummary;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.resolver.SelectionResolver;
import com.seeyon.ideaexport.storage.RecentPathStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导出失败路径测试，验证主流程会通过通知服务上报错误。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class ExportCommandFailureTest {

    @TempDir
    Path tempDir;

    /**
     * 验证选择解析失败时会走统一错误通知，而不是直接在后台线程弹框。
     */
    @Test
    void shouldNotifyErrorWhenSelectionResolveFails() {
        RecordingNotificationService notificationService = new RecordingNotificationService();
        ExportCommandService exportCommandService = new ExportCommandService(
                new FailingSelectionResolver(),
                new RecentPathStore(tempDir.resolve("config")),
                new PackagingMetadataService(),
                new PatchLayoutService(),
                new CompileService(),
                new FileExportService(),
                notificationService
        );

        exportCommandService.startExport((Project) null, (AnActionEvent) null);

        // 主流程入口失败也必须通过统一通知链路上报，避免 UI 调用散落在各处。
        assertEquals("未选择可导出文件", notificationService.lastErrorMessage);
    }

    /**
     * 固定抛出选择失败，模拟 Git 无显式选择等阻断场景。
     */
    private static class FailingSelectionResolver extends SelectionResolver {

        /**
         * 始终抛出选择失败异常。
         */
        @Override
        public List<SelectedItem> resolve(Project project, AnActionEvent event) throws ExportException {
            throw new ExportException("未选择可导出文件");
        }
    }

    /**
     * 记录通知内容，避免测试依赖真实 UI。
     */
    private static class RecordingNotificationService extends NotificationService {

        private String lastErrorMessage = "";

        /**
         * 记录错误消息，便于断言。
         */
        @Override
        public void notifyError(Project project, String message) {
            this.lastErrorMessage = message;
        }

        /**
         * 成功通知在本测试中不需要处理。
         */
        @Override
        public void notifyResult(Project project, ExportSummary summary, com.seeyon.ideaexport.model.CompileResult compileResult, Path targetPath) {
            // 本测试仅关注错误通知。
        }
    }
}
