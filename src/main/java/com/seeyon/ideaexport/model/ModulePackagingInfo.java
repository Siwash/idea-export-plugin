package com.seeyon.ideaexport.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 模块打包信息定义，用于 bug jar 模式下根据 Maven artifactId 计算目标 jar 目录。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public record ModulePackagingInfo(
        String moduleName,
        String artifactId,
        Path classesOutputDirectory
) {

    private static final String JAR_PREFIX = "seeyon-";
    private static final String JAR_SUFFIX = ".jar";

    /**
     * 校验打包信息的关键字段，保证路径规划时可直接使用。
     */
    public ModulePackagingInfo {
        Objects.requireNonNull(moduleName, "moduleName cannot be null");
        Objects.requireNonNull(artifactId, "artifactId cannot be null");
        Objects.requireNonNull(classesOutputDirectory, "classesOutputDirectory cannot be null");
        if (artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId cannot be blank");
        }
    }

    /**
     * 统一返回 bug jar 目录名，保证导出目录始终为 seeyon-<artifactId>.jar。
     *
     * @return jar 目录名
     */
    public String jarDirectoryName() {
        String jarBaseName = artifactId.endsWith(JAR_SUFFIX)
                ? artifactId.substring(0, artifactId.length() - JAR_SUFFIX.length())
                : artifactId;
        // bug jar 规范要求 lib 目录使用 seeyon-<artifactId>.jar，不能沿用 finalName 或模块名。
        if (!jarBaseName.startsWith(JAR_PREFIX)) {
            jarBaseName = JAR_PREFIX + jarBaseName;
        }
        return jarBaseName + JAR_SUFFIX;
    }
}
