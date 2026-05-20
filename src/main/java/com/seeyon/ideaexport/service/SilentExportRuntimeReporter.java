package com.seeyon.ideaexport.service;

/**
 * 空实现过程上报器，用于无过程窗口或测试场景下复用同一条业务链路。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public class SilentExportRuntimeReporter implements ExportRuntimeReporter {

    /**
     * 无界面场景下不处理开始事件。
     *
     * @param title 过程标题
     * @param totalSteps 总步骤数
     */
    @Override
    public void started(String title, int totalSteps) {
    }

    /**
     * 无界面场景下不处理阶段更新。
     *
     * @param stageText 阶段说明
     */
    @Override
    public void updateStage(String stageText) {
    }

    /**
     * 无界面场景下不处理进度更新。
     *
     * @param fraction 进度值
     */
    @Override
    public void updateProgress(double fraction) {
    }

    /**
     * 无界面场景下不处理日志追加。
     *
     * @param line 日志内容
     */
    @Override
    public void appendLog(String line) {
    }

    /**
     * 无界面场景下不处理结束事件。
     */
    @Override
    public void finished() {
    }
}
