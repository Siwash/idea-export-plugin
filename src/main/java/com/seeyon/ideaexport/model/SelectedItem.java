package com.seeyon.ideaexport.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 统一表示一次导出中被选中的文件项，屏蔽不同入口的选择差异。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public record SelectedItem(
        String moduleName,
        Path moduleBasePath,
        Path sourcePath,
        String relativePath,
        SourceType sourceType
) {

    /**
     * 校验选中项的关键字段，避免后续路径规划阶段再重复判空。
     */
    public SelectedItem {
        Objects.requireNonNull(moduleName, "moduleName cannot be null");
        Objects.requireNonNull(moduleBasePath, "moduleBasePath cannot be null");
        Objects.requireNonNull(sourcePath, "sourcePath cannot be null");
        Objects.requireNonNull(relativePath, "relativePath cannot be null");
        Objects.requireNonNull(sourceType, "sourceType cannot be null");
    }

    /**
     * 判断当前文件是否为 Java 源码。
     *
     * @return true 表示 Java 源码
     */
    public boolean isJavaSource() {
        return SourceType.JAVA_SOURCE == sourceType;
    }

    /**
     * 判断当前文件是否为 webapp 静态资源。
     *
     * @return true 表示 web 资源
     */
    public boolean isWebResource() {
        return SourceType.WEB_RESOURCE == sourceType;
    }
}
