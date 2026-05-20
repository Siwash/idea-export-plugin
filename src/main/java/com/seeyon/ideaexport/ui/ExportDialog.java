package com.seeyon.ideaexport.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.RowsDnDSupport;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.UIUtil;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileStrategy;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.SelectedItem;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 导出参数面板，负责采集导出模式、编译后端、编译策略、模块顺序、是否执行编译和目标路径。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public class ExportDialog extends DialogWrapper {

    private static final String FILE_VIEW_CARD = "file-view";
    private static final String MODULE_ORDER_CARD = "module-order";

    private final List<SelectedItem> selectedItems;
    private final JComboBox<ExportMode> exportModeComboBox;
    private final JComboBox<CompileMode> compileModeComboBox;
    private final JComboBox<String> recentPathComboBox;
    private final ComboboxWithBrowseButton targetPathField;
    private final JCheckBox skipCompileCheckBox;
    private final JBLabel selectionSummaryLabel;
    private final JBList<String> selectedItemsList;
    private final DefaultListModel<ModuleOrderItem> moduleOrderListModel;
    private final JBList<ModuleOrderItem> moduleOrderList;
    private final ContextHelpLabel layoutHelpLabel;
    private final JBRadioButton serialCompileRadioButton;
    private final JBRadioButton parallelCompileRadioButton;
    private final JPanel selectionContentPanel;
    private final CardLayout selectionCardLayout;

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
        this.targetPathField = new ComboboxWithBrowseButton(recentPathComboBox);
        this.skipCompileCheckBox = new JCheckBox("跳过编译，直接导出已存在产物");
        this.selectionSummaryLabel = new JBLabel();
        this.selectedItemsList = new JBList<>();
        this.moduleOrderListModel = new DefaultListModel<>();
        this.moduleOrderList = new JBList<>(moduleOrderListModel);
        this.layoutHelpLabel = ContextHelpLabel.create("导出结构说明", buildLayoutHint());
        this.serialCompileRadioButton = new JBRadioButton("串行编译（按左侧顺序）");
        this.parallelCompileRadioButton = new JBRadioButton("并行多线程编译（速度快）");
        this.selectionCardLayout = new CardLayout();
        this.selectionContentPanel = new JPanel(selectionCardLayout);
        initDefaults(project, recentPaths);
        init();
        setTitle("导出到 seeyon");
    }

    /**
     * 调整窗口尺寸，让分栏布局在常见分辨率下仍能保持紧凑和可读。
     */
    @Override
    protected void init() {
        super.init();
        setSize(1000, 660);
        setResizable(true);
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
                resolveCompileStrategy(),
                skipCompileCheckBox.isSelected(),
                Path.of(resolveTargetPathText()),
                resolveOrderedModuleBasePaths(),
                selectedItems
        ));
    }

    /**
     * 初始化默认值，确保默认编译方式、路径输入和模块顺序符合当前高频使用场景。
     *
     * @param project 当前项目
     * @param recentPaths 历史路径
     */
    private void initDefaults(Project project, List<String> recentPaths) {
        // 默认编译方式继续保持 Maven 当前工程，避免误触发全量 IDEA 编译。
        compileModeComboBox.setSelectedItem(CompileMode.MAVEN_CURRENT_MODULE);
        exportModeComboBox.setSelectedItem(ExportMode.STANDARD_PATCH);
        skipCompileCheckBox.setSelected(false);
        serialCompileRadioButton.setSelected(true);

        ButtonGroup compileStrategyButtonGroup = new ButtonGroup();
        compileStrategyButtonGroup.add(serialCompileRadioButton);
        compileStrategyButtonGroup.add(parallelCompileRadioButton);

        recentPathComboBox.setEditable(true);
        recentPathComboBox.setPrototypeDisplayValue("C:/Users/example/Desktop/86-seeyon");
        selectionSummaryLabel.setText(buildSelectionSummary());
        selectionSummaryLabel.setForeground(UIUtil.getContextHelpForeground());
        selectionSummaryLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        selectedItemsList.setListData(buildSelectedItemLines().toArray(String[]::new));
        selectedItemsList.setCellRenderer(buildSelectedItemRenderer());

        moduleOrderList.setCellRenderer(buildModuleOrderRenderer());
        loadModuleOrderItems();
        installModuleOrderSupport();
        selectionContentPanel.add(ScrollPaneFactory.createScrollPane(selectedItemsList, true), FILE_VIEW_CARD);
        selectionContentPanel.add(buildModuleOrderPanel(), MODULE_ORDER_CARD);

        skipCompileCheckBox.addActionListener(event -> updateCompileControlsState());
        compileModeComboBox.addActionListener(event -> updateCompileControlsState());
        exportModeComboBox.addActionListener(event -> updateExportModeState());
        serialCompileRadioButton.addActionListener(event -> updateSelectionView());
        parallelCompileRadioButton.addActionListener(event -> updateSelectionView());

        // 路径下拉和浏览按钮合并后，用户可以在同一控件里完成输入、回看历史和目录选择。
        targetPathField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        if (!recentPaths.isEmpty()) {
            recentPathComboBox.setSelectedItem(recentPaths.get(0));
        } else {
            recentPathComboBox.getEditor().setItem("");
        }
        updateExportModeState();
    }

    /**
     * 构建面板主体，改为顶部摘要加左右无边框分栏，贴近 JetBrains New UI 的信息层次。
     *
     * @return 面板组件
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel rootPanel = new JPanel(new BorderLayout(0, 0));
        rootPanel.setPreferredSize(new Dimension(980, 620));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        rootPanel.add(buildHeaderPanel(), BorderLayout.NORTH);

        JBSplitter splitter = new JBSplitter(false, 0.45F);
        splitter.setFirstComponent(buildSelectionPanel());
        splitter.setSecondComponent(buildFormPanel());
        rootPanel.add(splitter, BorderLayout.CENTER);
        return rootPanel;
    }

    /**
     * 构建顶部摘要区，用一句弱化文案承载本次导出范围，避免再出现大面积标题框。
     *
     * @return 顶部摘要区
     */
    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(selectionSummaryLabel, BorderLayout.CENTER);
        return headerPanel;
    }

    /**
     * 构建左侧内容区，串行模式展示模块排序，并行/IDEA/跳过编译时展示普通文件视图。
     *
     * @return 左侧列表区
     */
    private JPanel buildSelectionPanel() {
        JPanel selectionPanel = new JPanel(new BorderLayout(0, 10));
        selectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        selectionPanel.add(buildSelectionTitleLabel(), BorderLayout.NORTH);
        selectionPanel.add(selectionContentPanel, BorderLayout.CENTER);
        return selectionPanel;
    }

    /**
     * 构建右侧极简表单区，只保留高频输入控件，把说明文本折叠进帮助图标。
     *
     * @return 右侧表单区
     */
    private JPanel buildFormPanel() {
        JPanel formPanel = new JPanel(new BorderLayout(0, 12));
        formPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        formPanel.add(buildSectionTitle("参数配置"), BorderLayout.NORTH);
        formPanel.add(buildFormContent(), BorderLayout.NORTH);
        return formPanel;
    }

    /**
     * 构建表单内容区域，统一标签对齐并压缩无效留白。
     *
     * @return 表单内容
     */
    private JPanel buildFormContent() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 14, 12);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel exportModePanel = new JPanel(new BorderLayout(8, 0));
        exportModePanel.add(exportModeComboBox, BorderLayout.CENTER);
        exportModePanel.add(layoutHelpLabel, BorderLayout.EAST);

        JPanel compileStrategyPanel = new JPanel(new GridBagLayout());
        GridBagConstraints strategyConstraints = new GridBagConstraints();
        strategyConstraints.gridx = 0;
        strategyConstraints.gridy = 0;
        strategyConstraints.anchor = GridBagConstraints.WEST;
        strategyConstraints.insets = new Insets(0, 0, 6, 0);
        compileStrategyPanel.add(serialCompileRadioButton, strategyConstraints);
        strategyConstraints.gridy = 1;
        strategyConstraints.insets = new Insets(0, 0, 0, 0);
        compileStrategyPanel.add(parallelCompileRadioButton, strategyConstraints);

        addFormRow(contentPanel, gbc, 0, "导出模式", exportModePanel);
        addFormRow(contentPanel, gbc, 1, "编译方式", compileModeComboBox);
        addFormRow(contentPanel, gbc, 2, "编译策略", compileStrategyPanel);
        addIndentedRow(contentPanel, gbc, 3, skipCompileCheckBox);
        addFormRow(contentPanel, gbc, 4, "导出路径", targetPathField);
        return contentPanel;
    }

    /**
     * 添加标准表单行，保持标签和输入控件在不同分辨率下仍然稳定对齐。
     *
     * @param panel 表单面板
     * @param gbc 布局约束
     * @param row 行号
     * @param labelText 标签文案
     * @param component 输入控件
     */
    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0D;
        panel.add(new JBLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1D;
        panel.add(component, gbc);
    }

    /**
     * 添加缩进行，专门承载依附于上一行的补充选项，减少表单被无意义标题切断。
     *
     * @param panel 表单面板
     * @param gbc 布局约束
     * @param row 行号
     * @param component 输入控件
     */
    private void addIndentedRow(JPanel panel, GridBagConstraints gbc, int row, JComponent component) {
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 1D;
        panel.add(component, gbc);
    }

    /**
     * 统一创建区块标题，用排版而不是边框来表达层级。
     *
     * @param title 标题文案
     * @return 标题标签
     */
    private JBLabel buildSectionTitle(String title) {
        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 1F));
        return titleLabel;
    }

    /**
     * 构建左侧标题文案，让串行模式明确传达“当前顺序就是编译顺序”。
     *
     * @return 标题标签
     */
    private JBLabel buildSelectionTitleLabel() {
        return buildSectionTitle(isModuleOrderViewVisible() ? "待导出模块（按编译顺序）" : "待导出文件");
    }

    /**
     * 判断当前是否为源码导出模式，统一收敛模式判断入口。
     *
     * @return true 表示源码导出模式
     */
    private boolean isSourceExportMode() {
        ExportMode exportMode = (ExportMode) exportModeComboBox.getSelectedItem();
        return Objects.nonNull(exportMode) && exportMode.isSourceExport();
    }

    /**
     * 构建选中项渲染器，通过轻量缩进和行内留白模拟树状预览，而不是继续堆纯文本块。
     *
     * @return 列表渲染器
     */
    private DefaultListCellRenderer buildSelectedItemRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                // 列表只做轻量缩进和留白，让视觉更接近 JetBrains 的树列表而不是日志面板。
                label.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
                return label;
            }
        };
    }

    /**
     * 构建模块顺序渲染器，把串行编译关心的模块信息和文件数量放到一行里显示。
     *
     * @return 模块列表渲染器
     */
    private DefaultListCellRenderer buildModuleOrderRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                ModuleOrderItem moduleOrderItem = (ModuleOrderItem) value;
                label.setText(moduleOrderItem.moduleName() + "  (" + moduleOrderItem.fileCount() + " 项)");
                label.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
                return label;
            }
        };
    }

    /**
     * 构建模块排序面板，复用 IDEA 原生上下移动按钮和拖拽能力，而不是手写 DnD 事件。
     *
     * @return 模块排序面板
     */
    private JComponent buildModuleOrderPanel() {
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(moduleOrderList, new ModuleOrderEditableModel());
        toolbarDecorator.disableAddAction();
        toolbarDecorator.disableRemoveAction();
        toolbarDecorator.setScrollPaneBorder(BorderFactory.createEmptyBorder());
        toolbarDecorator.setPanelBorder(BorderFactory.createEmptyBorder());
        toolbarDecorator.setToolbarBorder(BorderFactory.createEmptyBorder());
        return toolbarDecorator.createPanel();
    }

    /**
     * 安装模块排序交互，确保串行模式下的列表既能拖拽，也能用原生上下按钮调整顺序。
     */
    private void installModuleOrderSupport() {
        RowsDnDSupport.install(moduleOrderList, new ModuleOrderEditableModel());
    }

    /**
     * 解析导出路径文本，统一兼容手工输入、历史选择和浏览按钮回填三种来源。
     *
     * @return 导出路径文本
     */
    private String resolveTargetPathText() {
        Object editorItem = recentPathComboBox.isEditable()
                ? recentPathComboBox.getEditor().getItem()
                : recentPathComboBox.getSelectedItem();
        return Objects.nonNull(editorItem) ? editorItem.toString().trim() : "";
    }

    /**
     * 返回当前选中的编译策略；界面上默认就是串行，保证多模块依赖场景更稳。
     *
     * @return 编译策略
     */
    private CompileStrategy resolveCompileStrategy() {
        return serialCompileRadioButton.isSelected() ? CompileStrategy.SERIAL : CompileStrategy.PARALLEL;
    }

    /**
     * 解析当前模块顺序；串行时按用户排序返回，并行/IDEA/跳过编译时仍保留当前默认顺序快照。
     *
     * @return 模块目录顺序
     */
    private List<Path> resolveOrderedModuleBasePaths() {
        List<Path> orderedModulePaths = new ArrayList<>(moduleOrderListModel.getSize());
        for (int index = 0; index < moduleOrderListModel.size(); index++) {
            orderedModulePaths.add(moduleOrderListModel.get(index).moduleBasePath());
        }
        return orderedModulePaths;
    }

    /**
     * 汇总本次选中的模块数、文件数和文件类型，用单行摘要替代原先占空间的大块说明区。
     *
     * @return 顶部摘要文本
     */
    private String buildSelectionSummary() {
        Set<String> moduleNames = new LinkedHashSet<>();
        int javaCount = 0;
        int webCount = 0;
        for (SelectedItem selectedItem : selectedItems) {
            moduleNames.add(selectedItem.moduleName());
            if (selectedItem.isJavaSource()) {
                javaCount++;
                continue;
            }
            if (selectedItem.isWebResource()) {
                webCount++;
            }
        }
        return "已选 " + selectedItems.size() + " 项文件 | 涉及模块: " + buildModuleSummary(moduleNames) + " | Java: " + javaCount + ", Web: " + webCount;
    }

    /**
     * 汇总模块名展示，模块过多时只保留前几项，避免顶部摘要被超长文案撑爆布局。
     *
     * @param moduleNames 模块名称集合
     * @return 模块摘要
     */
    private String buildModuleSummary(Set<String> moduleNames) {
        if (moduleNames.isEmpty()) {
            return "无";
        }
        List<String> moduleList = new ArrayList<>(moduleNames);
        int previewCount = Math.min(moduleList.size(), 3);
        String previewText = String.join(", ", moduleList.subList(0, previewCount));
        if (moduleList.size() > previewCount) {
            return previewText + " +" + (moduleList.size() - previewCount);
        }
        return previewText;
    }

    /**
     * 构建左侧列表内容，完整展示所有选中项，交给滚动区域处理超长场景。
     *
     * @return 列表项文本
     */
    private List<String> buildSelectedItemLines() {
        if (selectedItems.isEmpty()) {
            return List.of("› 无可导出的内容");
        }
        List<String> lines = new ArrayList<>(selectedItems.size());
        for (SelectedItem selectedItem : selectedItems) {
            String displayPath = isSourceExportMode() ? selectedItem.moduleRelativePath() : selectedItem.relativePath();
            // 源码模式必须展示模块相对路径，避免左侧预览继续误导用户以为会导出 .class 文件。
            lines.add("› [" + selectedItem.moduleName() + "] " + displayPath);
        }
        return lines;
    }

    /**
     * 生成模块顺序列表，默认按当前选中文件首次出现的模块顺序聚合，保证旧行为可预测。
     */
    private void loadModuleOrderItems() {
        moduleOrderListModel.clear();
        LinkedHashMap<String, ModuleOrderItem> moduleItems = new LinkedHashMap<>();
        for (SelectedItem selectedItem : selectedItems) {
            moduleItems.compute(selectedItem.moduleBasePath().toString(), (key, existingItem) -> {
                if (existingItem == null) {
                    return new ModuleOrderItem(selectedItem.moduleName(), selectedItem.moduleBasePath(), 1);
                }
                return existingItem.withFileCount(existingItem.fileCount() + 1);
            });
        }
        moduleItems.values().forEach(moduleOrderListModel::addElement);
    }

    /**
     * 根据当前模式同步编译控件与预览内容，源码模式下显式禁用所有编译相关交互。
     */
    private void updateExportModeState() {
        selectedItemsList.setListData(buildSelectedItemLines().toArray(String[]::new));
        layoutHelpLabel.setToolTipText(buildLayoutHint());
        updateCompileControlsState();
    }

    /**
     * 根据当前编译方式和跳过开关同步控件状态，避免用户在无效场景下调整 Maven 策略。
     */
    private void updateCompileControlsState() {
        ExportMode exportMode = (ExportMode) exportModeComboBox.getSelectedItem();
        boolean sourceExportMode = Objects.nonNull(exportMode) && exportMode.isSourceExport();
        boolean skipCompile = skipCompileCheckBox.isSelected();
        boolean mavenMode = CompileMode.MAVEN_CURRENT_MODULE == compileModeComboBox.getSelectedItem();

        if (sourceExportMode) {
            // 源码模式不使用跳过编译开关，进入该模式时主动归零，避免请求模型保留陈旧状态。
            skipCompileCheckBox.setSelected(false);
        }
        compileModeComboBox.setEnabled(!sourceExportMode && !skipCompile);
        skipCompileCheckBox.setEnabled(!sourceExportMode);
        serialCompileRadioButton.setEnabled(!sourceExportMode && !skipCompile && mavenMode);
        parallelCompileRadioButton.setEnabled(!sourceExportMode && !skipCompile && mavenMode);
        updateSelectionView();
    }

    /**
     * 根据策略切换左侧视图，让串行编译聚焦模块顺序，并行/IDEA/跳过编译保持普通文件视图。
     */
    private void updateSelectionView() {
        selectionCardLayout.show(selectionContentPanel, isModuleOrderViewVisible() ? MODULE_ORDER_CARD : FILE_VIEW_CARD);
    }

    /**
     * 判断当前是否应该显示模块顺序视图。
     *
     * @return 是否显示模块顺序视图
     */
    private boolean isModuleOrderViewVisible() {
        return !isSourceExportMode()
                && !skipCompileCheckBox.isSelected()
                && CompileMode.MAVEN_CURRENT_MODULE == compileModeComboBox.getSelectedItem()
                && serialCompileRadioButton.isSelected();
    }

    /**
     * 生成帮助提示内容，把规则说明折叠到问号图标中，避免继续占用主界面可视空间。
     *
     * @return 帮助提示 HTML
     */
    private String buildLayoutHint() {
        if (isSourceExportMode()) {
            return "<html>源码导出 → source/&lt;模块名&gt;/&lt;模块相对路径&gt;<br>"
                    + "源码模式直接复制原始文件，不参与 Maven 或 IDEA 编译。</html>";
        }
        return "<html>普通 Java → seeyon/WEB-INF/classes/<br>"
                + "Web 资源 → seeyon/...<br>"
                + "bug jar → seeyon/WEB-INF/lib/&lt;jar&gt;.jar/...<br><br>"
                + "Maven 串行模式支持调整模块顺序，并行模式保持普通文件视图。</html>";
    }

    /**
     * 模块顺序视图项，承载串行编译真正关心的模块路径与展示信息。
     *
     * @param moduleName 模块名
     * @param moduleBasePath 模块根路径
     * @param fileCount 该模块下的选中文件数
     */
    private record ModuleOrderItem(String moduleName, Path moduleBasePath, int fileCount) {

        /**
         * 返回带更新文件数的新视图项，避免直接修改 record 状态。
         *
         * @param nextFileCount 新文件数
         * @return 新视图项
         */
        private ModuleOrderItem withFileCount(int nextFileCount) {
            return new ModuleOrderItem(moduleName, moduleBasePath, nextFileCount);
        }
    }

    /**
     * 模块顺序模型，实现原生上下移动和拖拽交换能力，避免手写列表排序逻辑。
     */
    private class ModuleOrderEditableModel implements EditableModel {

        /**
         * 当前界面不支持用户删除模块，只允许调整顺序。
         *
         * @param index 行号
         */
        @Override
        public void removeRow(int index) {
        }

        /**
         * 当前界面不支持新增模块，模块来源必须忠实反映当前选中文件。
         */
        @Override
        public void addRow() {
        }

        /**
         * 交换两个模块位置，让串行编译顺序与列表顺序保持一致。
         *
         * @param oldIndex 原位置
         * @param newIndex 新位置
         */
        @Override
        public void exchangeRows(int oldIndex, int newIndex) {
            if (!canExchangeRows(oldIndex, newIndex)) {
                return;
            }
            ModuleOrderItem targetItem = moduleOrderListModel.get(oldIndex);
            moduleOrderListModel.set(oldIndex, moduleOrderListModel.get(newIndex));
            moduleOrderListModel.set(newIndex, targetItem);
            moduleOrderList.setSelectedIndex(newIndex);
        }

        /**
         * 只允许在有效索引范围内调整顺序，避免工具栏按钮和拖拽操作越界。
         *
         * @param oldIndex 原位置
         * @param newIndex 新位置
         * @return 是否允许交换
         */
        @Override
        public boolean canExchangeRows(int oldIndex, int newIndex) {
            return oldIndex >= 0 && newIndex >= 0 && oldIndex < moduleOrderListModel.size() && newIndex < moduleOrderListModel.size();
        }
    }
}
