package com.seeyon.ideaexport.service;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 后台进度桥接上报器，把过程摘要写入内存，同时同步更新 IDEA 原生进度条。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public class ProgressAwareRuntimeReporter implements ExportRuntimeReporter {

    private final BufferedExportRuntimeReporter delegate;
    private final ProgressIndicator progressIndicator;

    /**
     * 初始化桥接上报器。
     *
     * @param delegate 内存缓冲上报器
     * @param progressIndicator IDEA 原生进度指示器
     */
    public ProgressAwareRuntimeReporter(@NotNull BufferedExportRuntimeReporter delegate, @NotNull ProgressIndicator progressIndicator) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.progressIndicator = Objects.requireNonNull(progressIndicator, "progressIndicator cannot be null");
    }

    /**
     * 同时初始化内存态和进度条文本。
     *
     * @param title 过程标题
     * @param totalSteps 总步骤数
     */
    @Override
    public void started(String title, int totalSteps) {
        delegate.started(title, totalSteps);
        progressIndicator.setText(title);
        progressIndicator.setFraction(0D);
    }

    /**
     * 同步更新阶段文本。
     *
     * @param stageText 阶段说明
     */
    @Override
    public void updateStage(String stageText) {
        delegate.updateStage(stageText);
        progressIndicator.setText(stageText);
    }

    /**
     * 同步更新总体进度。
     *
     * @param fraction 进度值
     */
    @Override
    public void updateProgress(double fraction) {
        delegate.updateProgress(fraction);
        progressIndicator.setFraction(Math.max(0D, Math.min(1D, fraction)));
    }

    /**
     * 收集过程日志。
     *
     * @param line 日志内容
     */
    @Override
    public void appendLog(String line) {
        delegate.appendLog(line);
    }

    /**
     * 标记过程结束。
     */
    @Override
    public void finished() {
        delegate.finished();
        progressIndicator.setFraction(1D);
    }

    /**
     * 返回底层缓冲上报器，供结果窗口读取完整过程摘要。
     *
     * @return 缓冲上报器
     */
    public BufferedExportRuntimeReporter getDelegate() {
        return delegate;
    }
}
