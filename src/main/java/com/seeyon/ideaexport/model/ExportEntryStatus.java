package com.seeyon.ideaexport.model;

/**
 * 单文件导出状态定义，覆盖待执行、成功、跳过、失败四种结果。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public enum ExportEntryStatus {
    PENDING,
    EXPORTED,
    SKIPPED,
    FAILED
}
