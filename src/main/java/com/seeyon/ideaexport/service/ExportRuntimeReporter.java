package com.seeyon.ideaexport.service;

/**
 * 导出运行时上报接口，统一承接阶段、进度和日志输出。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public interface ExportRuntimeReporter {

    /**
     * 导出开始时初始化标题和总步骤数。
     *
     * @param title 过程标题
     * @param totalSteps 总步骤数
     */
    void started(String title, int totalSteps);

    /**
     * 更新当前阶段文本。
     *
     * @param stageText 阶段说明
     */
    void updateStage(String stageText);

    /**
     * 更新总体进度。
     *
     * @param fraction 0 到 1 的进度值
     */
    void updateProgress(double fraction);

    /**
     * 追加一行实时日志。
     *
     * @param line 日志内容
     */
    void appendLog(String line);

    /**
     * 导出结束时关闭上报状态。
     */
    void finished();
}
