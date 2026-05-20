package com.seeyon.ideaexport.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 一次导出操作的输入模型，包含导出模式、编译后端、编译策略、目标路径、是否执行编译和选中项。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public record ExportRequest(
        ExportMode mode,
        CompileMode compileMode,
        CompileStrategy compileStrategy,
        boolean skipCompile,
        Path targetPath,
        List<Path> orderedModuleBasePaths,
        List<SelectedItem> selectedItems
) {

    /**
     * 统一校验导出请求的关键字段，避免后续流程分散校验。
     */
    public ExportRequest {
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(compileMode, "compileMode cannot be null");
        Objects.requireNonNull(compileStrategy, "compileStrategy cannot be null");
        Objects.requireNonNull(targetPath, "targetPath cannot be null");
        Objects.requireNonNull(orderedModuleBasePaths, "orderedModuleBasePaths cannot be null");
        Objects.requireNonNull(selectedItems, "selectedItems cannot be null");
    }
}
