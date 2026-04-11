package com.seeyon.ideaexport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编译摘要关切测试，验证 IDEA 编译失败时不会只显示错误数。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class CompileServiceConcernTest {

    private final CompileService compileService = new CompileService();

    /**
     * 验证当上下文文本为空时，摘要会包含错误数和 Build 窗口指引。
     */
    @Test
    void shouldIncludeReadableFallbackWhenIdeaContextTextEmpty() {
        String summary = compileService.summarizeIdeaMessages("", 3);

        // 即使拿不到更细粒度消息，也必须明确错误数并告诉用户去哪里看详细错误。
        assertTrue(summary.contains("错误数: 3"));
        assertTrue(summary.contains("IDEA Build 窗口"));
    }

    /**
     * 验证当已有上下文文本时，优先返回上下文内容。
     */
    @Test
    void shouldPreferContextTextWhenAvailable() {
        String summary = compileService.summarizeIdeaMessages("真实错误摘要", 3);

        // 有更具体的上下文文本时，不应退化到通用提示。
        assertEquals("真实错误摘要", summary);
    }
}
