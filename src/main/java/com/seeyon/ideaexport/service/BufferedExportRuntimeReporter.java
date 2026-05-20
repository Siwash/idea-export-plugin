package com.seeyon.ideaexport.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 内存缓冲过程上报器，收集阶段、进度和日志摘要，供结果窗口统一展示。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public class BufferedExportRuntimeReporter implements ExportRuntimeReporter {

    private final CopyOnWriteArrayList<String> logLines = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> title = new AtomicReference<>("导出到 seeyon");
    private final AtomicReference<String> stage = new AtomicReference<>("准备开始导出...");
    private final AtomicReference<Double> progressFraction = new AtomicReference<>(0D);

    /**
     * 记录导出开始状态。
     *
     * @param title 过程标题
     * @param totalSteps 总步骤数
     */
    @Override
    public void started(String title, int totalSteps) {
        this.title.set(title);
        this.stage.set(title + "（共 " + totalSteps + " 步）");
        this.progressFraction.set(0D);
        this.logLines.clear();
        this.logLines.add("[START] 已创建导出任务，正在准备执行...");
    }

    /**
     * 记录当前阶段说明。
     *
     * @param stageText 阶段说明
     */
    @Override
    public void updateStage(String stageText) {
        this.stage.set(stageText);
    }

    /**
     * 记录总体进度。
     *
     * @param fraction 进度值
     */
    @Override
    public void updateProgress(double fraction) {
        this.progressFraction.set(fraction);
    }

    /**
     * 收集实时日志，供结果窗口聚合展示。
     *
     * @param line 日志内容
     */
    @Override
    public void appendLog(String line) {
        if (Objects.nonNull(line) && !line.isBlank()) {
            logLines.add(line);
        }
    }

    /**
     * 结束时不做额外动作，保留最终快照给结果窗口使用。
     */
    @Override
    public void finished() {
    }

    /**
     * 返回过程标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title.get();
    }

    /**
     * 返回最后阶段文本。
     *
     * @return 阶段文本
     */
    public String getStage() {
        return stage.get();
    }

    /**
     * 返回最后进度值。
     *
     * @return 进度值
     */
    public double getProgressFraction() {
        return progressFraction.get();
    }

    /**
     * 返回过程日志快照。
     *
     * @return 日志列表
     */
    public List<String> getLogLines() {
        return List.copyOf(logLines);
    }
}
