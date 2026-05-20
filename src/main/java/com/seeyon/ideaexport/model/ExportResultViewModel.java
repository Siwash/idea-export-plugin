package com.seeyon.ideaexport.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 导出结果视图模型，给独立结果窗口提供聚合统计和分组明细。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public record ExportResultViewModel(
        Path outputRootPath,
        String compileSummary,
        String processStage,
        List<String> processLogs,
        List<ExportEntry> successEntries,
        List<ExportEntry> failedEntries,
        List<ExportEntry> skippedEntries
) {

    /**
     * 统一校验结果视图字段，保证结果窗口始终基于完整模型渲染。
     */
    public ExportResultViewModel {
        Objects.requireNonNull(compileSummary, "compileSummary cannot be null");
        Objects.requireNonNull(processStage, "processStage cannot be null");
        Objects.requireNonNull(processLogs, "processLogs cannot be null");
        Objects.requireNonNull(successEntries, "successEntries cannot be null");
        Objects.requireNonNull(failedEntries, "failedEntries cannot be null");
        Objects.requireNonNull(skippedEntries, "skippedEntries cannot be null");
    }
}
