package com.seeyon.ideaexport.model;

/**
 * 导出模式定义，区分普通补丁与客户 bug jar 补丁。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public enum ExportMode {
    STANDARD_PATCH("普通补丁"),
    BUG_JAR("客户 bug jar");

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
     * 返回界面展示文案。
     *
     * @return 显示名称
     */
    @Override
    public String toString() {
        return displayName;
    }
}
