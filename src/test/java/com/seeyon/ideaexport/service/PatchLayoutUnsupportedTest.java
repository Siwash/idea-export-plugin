package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 不支持文件类型规划测试，验证导出流程会跳过不支持项而不是整体失败。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class PatchLayoutUnsupportedTest {

    private final PatchLayoutService patchLayoutService = new PatchLayoutService();

    /**
     * 验证不支持文件类型会被标记为 skipped。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldSkipUnsupportedFileType() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "demo-module",
                Path.of("/project/demo-module"),
                Path.of("/project/demo-module/readme.md"),
                "readme.md",
                SourceType.UNSUPPORTED
        );
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                false,
                Path.of("/export"),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(List.of("demo-module"), Map.of(), "编译成功");

        List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, Map.of());

        // 不支持类型只跳过当前文件，不能让整次导出直接失败。
        assertEquals(ExportEntryStatus.SKIPPED, entries.get(0).status());
        assertEquals("不支持的文件类型: " + selectedItem.sourcePath(), entries.get(0).message());
    }
}
