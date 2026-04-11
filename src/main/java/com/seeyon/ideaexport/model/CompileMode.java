package com.seeyon.ideaexport.model;

/**
 * 编译模式定义，默认只编译当前工程，也支持 IDEA 编译。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public enum CompileMode {
    MAVEN_CURRENT_MODULE("Maven 仅编译当前工程"),
    IDEA("IDEA 编译");

    private final String displayName;

    /**
     * 初始化编译模式显示名称，保证界面文案直接可读。
     *
     * @param displayName 显示名称
     */
    CompileMode(String displayName) {
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
