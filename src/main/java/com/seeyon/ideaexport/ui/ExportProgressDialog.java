package com.seeyon.ideaexport.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.seeyon.ideaexport.service.ExportRuntimeReporter;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * 导出过程窗口，负责展示阶段、进度和实时日志输出。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public class ExportProgressDialog extends DialogWrapper implements ExportRuntimeReporter {

    private final JLabel stageLabel;
    private final JProgressBar progressBar;
    private final JBTextArea logTextArea;

    /**
     * 初始化导出过程窗口。
     *
     * @param project 当前项目
     */
    public ExportProgressDialog(Project project) {
        super(project, false);
        this.stageLabel = new JLabel("准备开始导出...");
        this.progressBar = new JProgressBar(0, 100);
        this.logTextArea = new JBTextArea();
        this.logTextArea.setEditable(false);
        this.logTextArea.setLineWrap(false);
        this.logTextArea.setRows(20);
        this.logTextArea.setColumns(120);
        init();
        setTitle("导出进行中");
    }

    /**
     * 初始化过程窗口的默认尺寸，让实时日志区域有足够可读空间。
     */
    @Override
    protected void init() {
        super.init();
        setSize(920, 620);
    }

    /**
     * 构建窗口主体，分为阶段区、进度区和日志区。
     *
     * @return 中央面板
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setPreferredSize(new Dimension(900, 560));

        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("执行状态"));
        topPanel.add(stageLabel, BorderLayout.NORTH);
        topPanel.add(progressBar, BorderLayout.SOUTH);

        // 实时日志是这个窗口的主体区域，必须占据主要空间，不能再被压缩成底部一条细线。
        JBScrollPane logScrollPane = new JBScrollPane(logTextArea);
        logScrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder("实时输出"));

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(logScrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 过程窗口不允许中途通过默认 OK 行为关闭，避免误操作打断观察过程。
     */
    @Override
    protected javax.swing.Action @Nullable [] createActions() {
        return new javax.swing.Action[0];
    }

    /**
     * 导出开始时写入标题和初始进度。
     *
     * @param title 过程标题
     * @param totalSteps 总步骤数
     */
    @Override
    public void started(String title, int totalSteps) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // 过程窗口打开后立即展示第一屏状态，避免用户等待时无反馈。
            stageLabel.setText(title + "（共 " + totalSteps + " 步）");
            progressBar.setValue(0);
            logTextArea.setText("");
        });
    }

    /**
     * 更新当前阶段文字。
     *
     * @param stageText 阶段说明
     */
    @Override
    public void updateStage(String stageText) {
        ApplicationManager.getApplication().invokeLater(() -> stageLabel.setText(stageText));
    }

    /**
     * 更新总体进度条。
     *
     * @param fraction 0 到 1 的进度值
     */
    @Override
    public void updateProgress(double fraction) {
        ApplicationManager.getApplication().invokeLater(() -> progressBar.setValue((int) Math.round(Math.max(0D, Math.min(1D, fraction)) * 100)));
    }

    /**
     * 追加实时日志，并保持文本区始终滚动到末尾。
     *
     * @param line 日志内容
     */
    @Override
    public void appendLog(String line) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // 实时输出直接 append，避免每来一行都重建整块文本导致大日志场景卡顿。
            logTextArea.append(line + System.lineSeparator());
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    /**
     * 导出结束后把进度条拉满，明确告诉用户过程已结束。
     */
    @Override
    public void finished() {
        ApplicationManager.getApplication().invokeLater(() -> {
            progressBar.setValue(100);
            // 结果窗口接管后，过程窗口应自动收束，避免两个窗口同时悬挂影响体验。
            close(OK_EXIT_CODE);
        });
    }
}
