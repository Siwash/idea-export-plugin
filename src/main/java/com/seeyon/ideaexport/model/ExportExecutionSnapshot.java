package com.seeyon.ideaexport.model;

import java.util.List;
import java.util.Objects;

/**
 * 导出过程快照模型，给过程窗口提供当前阶段、进度和日志内容。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public record ExportExecutionSnapshot(
        String title,
        String stage,
        double progressFraction,
        List<String> consoleLines
) {

    /**
     * 统一校验过程快照字段，确保过程窗口读取到的是稳定状态。
     */
    public ExportExecutionSnapshot {
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(stage, "stage cannot be null");
        Objects.requireNonNull(consoleLines, "consoleLines cannot be null");
    }
}
