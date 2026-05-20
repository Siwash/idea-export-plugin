package com.seeyon.ideaexport.model;

/**
 * 导出模式定义，区分普通补丁、客户 bug jar 与源码导出。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public enum ExportMode {
    STANDARD_PATCH("普通补丁"),
    BUG_JAR("客户 bug jar"),
    SOURCE_EXPORT("源码导出");

    private final String displayName;

    /**
     * 初始化导出模式显示名称，避免界面直接暴露枚举常量名。
     *
     * @param displayName 显示名称
     */
    ExportMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 判断当前模式是否为源码导出，避免调用方直接散落枚举比较。
     *
     * @return true 表示源码导出模式
     */
    public boolean isSourceExport() {
        return this == SOURCE_EXPORT;
    }

    /**
     * 返回界面展示文案。
     *
     * @return 显示名称
     */
    @Override
    public String toString() {
        return displayName;
    }
}
