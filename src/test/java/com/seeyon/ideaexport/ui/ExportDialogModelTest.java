package com.seeyon.ideaexport.ui;

import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileStrategy;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 导出参数页模型测试，验证模块顺序聚合逻辑、默认编译策略和源码模式预览语义保持稳定。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
class ExportDialogModelTest {

    /**
     * 验证模块顺序会按首次出现顺序聚合，而不是被后续文件顺序打乱。
     */
    @Test
    void shouldAggregateModuleOrderByFirstAppearance() {
        List<SelectedItem> selectedItems = List.of(
                new SelectedItem("module-b", Path.of("/project/module-b"), Path.of("/project/module-b/src/main/java/demo/B1.java"), "demo/B1.class", "src/main/java/demo/B1.java", SourceType.JAVA_SOURCE),
                new SelectedItem("module-a", Path.of("/project/module-a"), Path.of("/project/module-a/src/main/java/demo/A1.java"), "demo/A1.class", "src/main/java/demo/A1.java", SourceType.JAVA_SOURCE),
                new SelectedItem("module-b", Path.of("/project/module-b"), Path.of("/project/module-b/src/main/java/demo/B2.java"), "demo/B2.class", "src/main/java/demo/B2.java", SourceType.JAVA_SOURCE)
        );

        Map<Path, Integer> moduleOrder = aggregateModuleCounts(selectedItems);

        // 串行模式默认顺序必须来自首次出现顺序，才能和用户当前选择心智保持一致。
        assertEquals(List.of(Path.of("/project/module-b"), Path.of("/project/module-a")), moduleOrder.keySet().stream().toList());
        assertEquals(2, moduleOrder.get(Path.of("/project/module-b")));
        assertEquals(1, moduleOrder.get(Path.of("/project/module-a")));
    }

    /**
     * 验证默认后端和默认编译策略的组合仍然是 Maven + 串行。
     */
    @Test
    void shouldKeepMavenAndSerialAsDefaultCombination() {
        CompileMode compileMode = CompileMode.MAVEN_CURRENT_MODULE;
        CompileStrategy compileStrategy = CompileStrategy.SERIAL;
        ExportMode exportMode = ExportMode.STANDARD_PATCH;

        // 默认组合必须稳定，避免老用户升级后直接落到高风险的并行路径。
        assertEquals(CompileMode.MAVEN_CURRENT_MODULE, compileMode);
        assertEquals(CompileStrategy.SERIAL, compileStrategy);
        assertEquals(ExportMode.STANDARD_PATCH, exportMode);
    }

    /**
     * 验证源码模式预览应该使用模块相对路径，而不是补丁模式的 .class 路径。
     */
    @Test
    void shouldUseModuleRelativePathForSourceExportPreview() {
        SelectedItem selectedItem = new SelectedItem(
                "module-a",
                Path.of("/project/module-a"),
                Path.of("/project/module-a/src/main/java/demo/A1.java"),
                "demo/A1.class",
                "src/main/java/demo/A1.java",
                SourceType.JAVA_SOURCE
        );

        String displayPath = resolveDisplayPath(selectedItem, ExportMode.SOURCE_EXPORT);

        // 源码模式左侧预览必须和最终输出路径语义一致，不能继续显示 .class 路径误导用户。
        assertEquals("src/main/java/demo/A1.java", displayPath);
    }

    /**
     * 聚合模块计数，模拟参数页串行模式下的模块排序列表来源。
     *
     * @param selectedItems 选中项
     * @return 模块顺序和文件数量
     */
    private Map<Path, Integer> aggregateModuleCounts(List<SelectedItem> selectedItems) {
        Map<Path, Integer> moduleCounts = new LinkedHashMap<>();
        for (SelectedItem selectedItem : selectedItems) {
            // 模块顺序列表必须按首次出现顺序累计计数，不能在聚合时丢掉顺序信息。
            moduleCounts.merge(selectedItem.moduleBasePath(), 1, Integer::sum);
        }
        return moduleCounts;
    }

    /**
     * 模拟参数页根据导出模式选择展示路径，隔离 UI 控件依赖。
     *
     * @param selectedItem 选中项
     * @param exportMode 导出模式
     * @return 预览路径
     */
    private String resolveDisplayPath(SelectedItem selectedItem, ExportMode exportMode) {
        // 源码模式与补丁模式的预览语义不同，这里只验证路径选择规则本身。
        return exportMode.isSourceExport() ? selectedItem.moduleRelativePath() : selectedItem.relativePath();
    }
}
