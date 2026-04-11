package com.seeyon.ideaexport.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.SelectedItem;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 导出参数面板，负责采集导出模式、编译模式、是否执行编译和目标路径。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class ExportDialog extends DialogWrapper {

    private final List<SelectedItem> selectedItems;
    private final JComboBox<ExportMode> exportModeComboBox;
    private final JComboBox<CompileMode> compileModeComboBox;
    private final JComboBox<String> recentPathComboBox;
    private final TextFieldWithBrowseButton targetPathField;
    private final JCheckBox skipCompileCheckBox;

    /**
     * 初始化导出参数面板。
     *
     * @param project 当前项目
     * @param selectedItems 选中项列表
     * @param recentPaths 历史导出路径
     */
    public ExportDialog(Project project, List<SelectedItem> selectedItems, List<String> recentPaths) {
        super(project);
        this.selectedItems = List.copyOf(selectedItems);
        this.exportModeComboBox = new JComboBox<>(ExportMode.values());
        this.compileModeComboBox = new JComboBox<>(CompileMode.values());
        this.recentPathComboBox = new JComboBox<>(recentPaths.toArray(String[]::new));
        this.targetPathField = new TextFieldWithBrowseButton();
        this.skipCompileCheckBox = new JCheckBox("跳过编译，直接导出已存在产物");
        initDefaults(project, recentPaths);
        init();
        setTitle("导出到 seeyon");
    }

    /**
     * 展示面板并返回导出请求；用户取消时返回空。
     *
     * @return 导出请求
     */
    public Optional<ExportRequest> showAndGetRequest() {
        if (!showAndGet()) {
            return Optional.empty();
        }
        return Optional.of(new ExportRequest(
                Objects.requireNonNull((ExportMode) exportModeComboBox.getSelectedItem()),
                Objects.requireNonNull((CompileMode) compileModeComboBox.getSelectedItem()),
                skipCompileCheckBox.isSelected(),
                Path.of(targetPathField.getText().trim()),
                selectedItems
        ));
    }

    /**
     * 初始化默认值，确保默认编译方式和最近路径符合需求。
     *
     * @param recentPaths 历史路径
     */
    private void initDefaults(Project project, List<String> recentPaths) {
        // 默认编译方式必须是 Maven 当前工程编译，避免大项目误触发全量 IDEA 编译。
        compileModeComboBox.setSelectedItem(CompileMode.MAVEN_CURRENT_MODULE);
        exportModeComboBox.setSelectedItem(ExportMode.STANDARD_PATCH);
        skipCompileCheckBox.setSelected(false);
        skipCompileCheckBox.addActionListener(event -> {
            boolean skipCompile = skipCompileCheckBox.isSelected();
            // 关闭编译开关后仍保留当前编译模式，仅禁用选择框，便于用户重新打开时恢复原选择。
            compileModeComboBox.setEnabled(!skipCompile);
        });
        targetPathField.addBrowseFolderListener(
                "选择导出路径",
                "选择导出到 seeyon 的目标目录",
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
        );
        if (!recentPaths.isEmpty()) {
            recentPathComboBox.setSelectedItem(recentPaths.get(0));
            targetPathField.setText(recentPaths.get(0));
        }
        recentPathComboBox.addActionListener(event -> {
            Object selected = recentPathComboBox.getSelectedItem();
            if (Objects.nonNull(selected)) {
                // 历史路径与输入框联动，减少用户重复粘贴路径操作。
                targetPathField.setText(selected.toString());
            }
        });
    }

    /**
     * 构建面板主体。
     *
     * @return 面板组件
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("导出模式", exportModeComboBox)
                .addLabeledComponent("编译方式", compileModeComboBox)
                .addComponent(skipCompileCheckBox)
                .addLabeledComponent("历史路径", recentPathComboBox)
                .addLabeledComponent("导出路径", targetPathField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        return panel;
    }
}
