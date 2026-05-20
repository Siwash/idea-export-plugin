package com.seeyon.ideaexport.model;

/**
 * Maven 编译策略定义，区分按顺序逐模块执行与并行多线程执行。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public enum CompileStrategy {
    SERIAL("串行编译"),
    PARALLEL("并行多线程编译");

    private final String displayName;

    /**
     * 初始化策略显示名称，保证界面文案可直接复用。
     *
     * @param displayName 显示名称
     */
    CompileStrategy(String displayName) {
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
