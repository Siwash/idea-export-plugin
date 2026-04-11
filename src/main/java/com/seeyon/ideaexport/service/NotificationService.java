package com.seeyon.ideaexport.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportSummary;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 导出结果通知服务，负责把执行结果以可读方式展示给用户。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class NotificationService {

    /**
     * 展示导出结果摘要。
     *
     * @param project 当前项目
     * @param summary 导出结果汇总
     * @param compileResult 编译结果
     * @param targetPath 导出目录
     */
    public void notifyResult(Project project, ExportSummary summary, CompileResult compileResult, Path targetPath) {
        Objects.requireNonNull(summary, "summary cannot be null");
        Objects.requireNonNull(compileResult, "compileResult cannot be null");
        Objects.requireNonNull(targetPath, "targetPath cannot be null");
        String message = buildSummaryMessage(summary, compileResult, targetPath);
        if (summary.failedCount() > 0) {
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, message, "导出到 seeyon"));
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> Messages.showInfoMessage(project, message, "导出到 seeyon"));
    }

    /**
     * 展示错误提示，统一保证后台线程中的 UI 调用切回 EDT。
     *
     * @param project 当前项目
     * @param message 错误消息
     */
    public void notifyError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, message, "导出到 seeyon"));
    }

    /**
     * 构建导出结果摘要文本。
     *
     * @param summary 导出汇总
     * @param compileResult 编译结果
     * @param targetPath 导出目录
     * @return 结果文本
     */
    String buildSummaryMessage(ExportSummary summary, CompileResult compileResult, Path targetPath) {
        StringBuilder messageBuilder = new StringBuilder();
        // 结果摘要必须直接告诉用户输出位置，避免成功后还要自行猜测目录。
        messageBuilder.append("导出完成\n")
                .append("输出位置: ").append(targetPath).append("\n")
                .append("编译: ").append(compileResult.summary()).append("\n")
                .append("成功: ").append(summary.successCount()).append("\n")
                .append("失败: ").append(summary.failedCount()).append("\n")
                .append("跳过: ").append(summary.skippedCount());

        appendEntryDetails(messageBuilder, summary);
        return messageBuilder.toString();
    }

    /**
     * 追加成功、失败和跳过明细，满足部分成功部分失败时的结果可读性要求。
     *
     * @param messageBuilder 消息构建器
     * @param summary 导出汇总
     */
    private void appendEntryDetails(StringBuilder messageBuilder, ExportSummary summary) {
        if (summary.entries().isEmpty()) {
            return;
        }

        messageBuilder.append("\n\n明细:");
        for (var entry : summary.entries()) {
            messageBuilder.append("\n- ")
                    .append(entry.status())
                    .append(" | ")
                    .append(entry.outputPath())
                    .append(" | ")
                    .append(resolveMessage(entry));
        }
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
