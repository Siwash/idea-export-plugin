package com.seeyon.ideaexport.model;

import java.util.List;
import java.util.Objects;

/**
 * 历史导出路径持久化模型，保持 JSON 结构稳定。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public record RecentPathState(List<String> paths) {

    /**
     * 统一校验历史路径列表，避免存储层反复处理空集合。
     */
    public RecentPathState {
        Objects.requireNonNull(paths, "paths cannot be null");
    }

    /**
     * 返回空状态，便于首次初始化时直接落盘。
     *
     * @return 空历史路径状态
     */
    public static RecentPathState empty() {
        return new RecentPathState(List.of());
    }
}
