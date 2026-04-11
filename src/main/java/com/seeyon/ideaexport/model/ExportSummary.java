package com.seeyon.ideaexport.model;

import java.util.List;
import java.util.Objects;

/**
 * 导出完成后的汇总模型，用于结果通知和联调断言。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public record ExportSummary(
        List<ExportEntry> entries,
        int successCount,
        int failedCount,
        int skippedCount
) {

    /**
     * 统一校验结果集字段，保证通知展示与测试断言稳定。
     */
    public ExportSummary {
        Objects.requireNonNull(entries, "entries cannot be null");
    }
}
