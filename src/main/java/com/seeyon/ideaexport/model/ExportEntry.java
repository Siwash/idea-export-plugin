package com.seeyon.ideaexport.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 单文件导出计划与结果模型，既描述目标路径，也承载执行状态。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public record ExportEntry(
        String moduleName,
        Path sourcePath,
        Path outputPath,
        ExportEntryStatus status,
        String message,
        int line,
        int column
) {

    /**
     * 统一校验导出项的关键字段，确保执行阶段不再处理非法模型。
     */
    public ExportEntry {
        Objects.requireNonNull(moduleName, "moduleName cannot be null");
        Objects.requireNonNull(outputPath, "outputPath cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }

    /**
     * 生成待执行状态的导出项，避免调用方重复填充默认值。
     *
     * @param moduleName 模块名
     * @param sourcePath 源文件路径
     * @param outputPath 目标文件路径
     * @return 待执行导出项
     */
    public static ExportEntry pending(String moduleName, Path sourcePath, Path outputPath) {
        // 导出计划阶段统一使用 PENDING，执行完成后再替换真实状态。
        return new ExportEntry(moduleName, sourcePath, outputPath, ExportEntryStatus.PENDING, "", -1, -1);
    }

    /**
     * 基于现有导出项生成新状态，避免外部重复拷贝字段。
     *
     * @param nextStatus 新状态
     * @param nextMessage 新消息
     * @return 新导出项
     */
    public ExportEntry withStatus(ExportEntryStatus nextStatus, String nextMessage) {
        return new ExportEntry(moduleName, sourcePath, outputPath, nextStatus, nextMessage, line, column);
    }

    /**
     * 基于现有导出项补充失败定位信息。
     *
     * @param nextLine 行号
     * @param nextColumn 列号
     * @return 带定位信息的新导出项
     */
    public ExportEntry withLocation(int nextLine, int nextColumn) {
        return new ExportEntry(moduleName, sourcePath, outputPath, status, message, nextLine, nextColumn);
    }
}
