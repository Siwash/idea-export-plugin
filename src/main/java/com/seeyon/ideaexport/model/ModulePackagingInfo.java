package com.seeyon.ideaexport.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 模块打包信息定义，用于 bug jar 模式下计算目标目录和编译产物路径。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public record ModulePackagingInfo(
        String moduleName,
        String finalName,
        Path classesOutputDirectory
) {

    /**
     * 校验打包信息的关键字段，保证路径规划时可直接使用。
     */
    public ModulePackagingInfo {
        Objects.requireNonNull(moduleName, "moduleName cannot be null");
        Objects.requireNonNull(finalName, "finalName cannot be null");
        Objects.requireNonNull(classesOutputDirectory, "classesOutputDirectory cannot be null");
    }
}
