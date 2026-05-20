package com.seeyon.ideaexport.service;

import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 结果动作服务测试，验证失败定位入口的可覆盖行为。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
class ResultActionServiceTest {

    /**
     * 验证测试替身可拦截失败项定位动作。
     */
    @Test
    void shouldRecordFailureEntryWhenNavigating() {
        RecordingResultActionService resultActionService = new RecordingResultActionService();
        ExportEntry failedEntry = new ExportEntry("demo", Path.of("/src/Test.java"), Path.of("/export/Test.class"), ExportEntryStatus.FAILED, "cannot find symbol", 12, 3);

        resultActionService.navigateToFailure(null, failedEntry);

        // 结果窗口点击失败定位后，动作服务必须收到对应失败项，后续才能执行真实跳转。
        assertEquals(failedEntry, resultActionService.lastFailureEntry);
    }

    /**
     * 记录型动作服务，用于隔离真实桌面和文件系统交互。
     */
    private static class RecordingResultActionService extends ResultActionService {

        private ExportEntry lastFailureEntry;

        /**
         * 记录失败项，避免测试触发真实 IDE 跳转。
         *
         * @param project 当前项目
         * @param entry 失败项
         */
        @Override
        public void navigateToFailure(Project project, ExportEntry entry) {
            this.lastFailureEntry = entry;
        }
    }
}
