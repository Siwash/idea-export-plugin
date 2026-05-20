package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.CompileStrategy;
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
 * 不支持文件类型规划测试，验证补丁模式会跳过不支持项，而源码模式会直接导出原文件。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class PatchLayoutUnsupportedTest {

    private final PatchLayoutService patchLayoutService = new PatchLayoutService();

    /**
     * 验证不支持文件类型在补丁模式会被标记为 skipped。
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
                "readme.md",
                SourceType.UNSUPPORTED
        );
        ExportRequest request = new ExportRequest(
                ExportMode.STANDARD_PATCH,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );
        CompileResult compileResult = CompileResult.success(List.of("demo-module"), Map.of(), "编译成功");

        List<ExportEntry> entries = patchLayoutService.plan(request, compileResult, Map.of());

        // 不支持类型只跳过当前文件，不能让整次导出直接失败。
        assertEquals(ExportEntryStatus.SKIPPED, entries.get(0).status());
        assertEquals("不支持的文件类型: " + selectedItem.sourcePath(), entries.get(0).message());
    }

    /**
     * 验证源码模式下不会再把原本补丁模式的不支持文件标记为 skipped。
     *
     * @throws ExportException 规划失败
     */
    @Test
    void shouldExportUnsupportedFileInSourceMode() throws ExportException {
        SelectedItem selectedItem = new SelectedItem(
                "demo-module",
                Path.of("/project/demo-module"),
                Path.of("/project/demo-module/readme.md"),
                "readme.md",
                "readme.md",
                SourceType.UNSUPPORTED
        );
        ExportRequest request = new ExportRequest(
                ExportMode.SOURCE_EXPORT,
                CompileMode.MAVEN_CURRENT_MODULE,
                CompileStrategy.SERIAL,
                false,
                Path.of("/export"),
                List.of(selectedItem.moduleBasePath()),
                List.of(selectedItem)
        );

        List<ExportEntry> entries = patchLayoutService.plan(request, CompileResult.success(List.of(), Map.of(), "源码导出无需编译"), Map.of());

        // 源码模式要求导出任意选中文件，因此 readme 这类文件也必须直接生成待导出条目。
        assertEquals(ExportEntryStatus.PENDING, entries.get(0).status());
        assertEquals(Path.of("/project/demo-module/readme.md"), entries.get(0).sourcePath());
        assertEquals(Path.of("/export/source/demo-module/readme.md"), entries.get(0).outputPath());
    }
}
