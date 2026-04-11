package com.seeyon.ideaexport.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 编译阶段输出模型，统一向导出主流程传递编译结果、摘要和输出目录。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public record CompileResult(
        boolean success,
        String summary,
        List<String> compiledModules,
        Map<String, Path> moduleOutputDirectories
) {

    /**
     * 统一校验编译结果字段，保证通知层可以直接展示。
     */
    public CompileResult {
        Objects.requireNonNull(summary, "summary cannot be null");
        Objects.requireNonNull(compiledModules, "compiledModules cannot be null");
        Objects.requireNonNull(moduleOutputDirectories, "moduleOutputDirectories cannot be null");
    }

    /**
     * 创建成功结果，避免重复拼装成功消息。
     *
     * @param compiledModules 已编译模块
     * @param moduleOutputDirectories 模块输出目录
     * @param summary 成功摘要
     * @return 成功编译结果
     */
    public static CompileResult success(List<String> compiledModules, Map<String, Path> moduleOutputDirectories, String summary) {
        // 成功场景需要保留模块信息和输出目录，后续导出必须据此定位真实 class 文件。
        return new CompileResult(true, summary, compiledModules, moduleOutputDirectories);
    }

    /**
     * 创建成功结果并使用默认摘要。
     *
     * @param compiledModules 已编译模块
     * @param moduleOutputDirectories 模块输出目录
     * @return 成功编译结果
     */
    public static CompileResult success(List<String> compiledModules, Map<String, Path> moduleOutputDirectories) {
        return success(compiledModules, moduleOutputDirectories, "编译成功");
    }

    /**
     * 创建失败结果，统一摘要入口。
     *
     * @param summary 失败摘要
     * @return 失败编译结果
     */
    public static CompileResult failure(String summary) {
        return new CompileResult(false, summary, List.of(), Map.of());
    }
}
