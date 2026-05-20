package com.seeyon.ideaexport.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.UIUtil;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportResultViewModel;
import com.seeyon.ideaexport.service.ResultActionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导出结果窗口，负责聚合展示成功/失败/跳过结果和快捷操作。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public class ExportResultDialog extends DialogWrapper {

    private static final Pattern WINDOWS_OR_UNIX_LOCATION_PATTERN = Pattern.compile("(?:\\[[^\\]]+\\]\\s+)?((?:[A-Za-z]:[/\\\\]|/).+?\\.java):\\[(\\d+),(\\d+)\\]");
    private static final Pattern IDEA_SUMMARY_PATTERN = Pattern.compile("((?:[A-Za-z]:[/\\\\]|/).+?\\.java)\\s+-\\s+.+");

    private final Project project;
    private final ExportResultViewModel viewModel;
    private final ResultActionService resultActionService;
    private final ConsoleView processConsole;

    /**
     * 初始化结果窗口。
     *
     * @param project 当前项目
     * @param viewModel 结果视图模型
     * @param resultActionService 结果动作服务
     */
    public ExportResultDialog(Project project, ExportResultViewModel viewModel, ResultActionService resultActionService) {
        super(project, false);
        this.project = project;
        this.viewModel = viewModel;
        this.resultActionService = resultActionService;
        this.processConsole = createProcessConsole(project);
        init();
        Disposer.register(getDisposable(), processConsole);
        setTitle("导出结果");
    }

    /**
     * 调整窗口尺寸，让状态头和结果明细在同一屏内保持高利用率布局。
     */
    @Override
    protected void init() {
        super.init();
        setSize(1040, 760);
        setResizable(true);
    }

    /**
     * 构建结果窗口主体，改为状态头加无边框 Tab 主区，避免结果页继续堆砌重复标题框。
     *
     * @return 中央面板
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel rootPanel = new JPanel(new BorderLayout(0, 12));
        rootPanel.setPreferredSize(new Dimension(1000, 700));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        rootPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        rootPanel.add(buildTabbedPanel(), BorderLayout.CENTER);
        return rootPanel;
    }

    /**
     * 仅保留左下角“打开目录”，把辅助动作移出主视区，减少界面噪音。
     *
     * @return 左侧动作
     */
    @Override
    protected Action @NotNull [] createLeftSideActions() {
        AbstractAction openDirectoryAction = new AbstractAction("打开目录") {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (viewModel.outputRootPath() != null) {
                    resultActionService.openOutputDirectory(project, viewModel.outputRootPath());
                }
            }
        };
        openDirectoryAction.setEnabled(viewModel.outputRootPath() != null);
        return new Action[]{openDirectoryAction};
    }

    /**
     * 结果页右下角只保留关闭按钮，避免“确定/取消”制造无意义决策负担。
     *
     * @return 右侧动作
     */
    @Override
    protected Action @NotNull [] createActions() {
        Action closeAction = getCancelAction();
        closeAction.putValue(Action.NAME, "关闭");
        return new Action[]{closeAction};
    }

    /**
     * 构建顶部全局状态区，只展示状态、计数和输出位置，不再把长摘要文本塞进头部。
     *
     * @return 顶部状态区
     */
    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JPanel statusRowPanel = new JPanel(new BorderLayout(12, 0));
        statusRowPanel.add(buildStatusTitlePanel(), BorderLayout.WEST);
        statusRowPanel.add(buildMetricsLabel(), BorderLayout.EAST);

        JPanel outputPathPanel = new JPanel(new BorderLayout(8, 0));
        outputPathPanel.add(new JBLabel("输出位置:"), BorderLayout.WEST);
        outputPathPanel.add(buildOutputPathComponent(), BorderLayout.CENTER);

        headerPanel.add(statusRowPanel, BorderLayout.NORTH);
        headerPanel.add(outputPathPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    /**
     * 构建带图标的主状态标题，让用户第一眼知道本次导出是成功还是失败。
     *
     * @return 主状态标题区
     */
    private JPanel buildStatusTitlePanel() {
        JPanel titlePanel = new JPanel(new BorderLayout(10, 0));

        JLabel iconLabel = new JLabel(hasFailures() ? AllIcons.General.ErrorDialog : AllIcons.RunConfigurations.TestPassed);
        JBLabel titleLabel = new JBLabel(resolveStatusTitle());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 3F));

        titlePanel.add(iconLabel, BorderLayout.WEST);
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        return titlePanel;
    }

    /**
     * 构建右侧统计文案，让结果计数保持稳定位置，不再混在大段摘要里抢阅读焦点。
     *
     * @return 统计标签
     */
    private JBLabel buildMetricsLabel() {
        JBLabel metricsLabel = new JBLabel(
                "成功: " + viewModel.successEntries().size()
                        + "  失败: " + viewModel.failedEntries().size()
                        + "  跳过: " + viewModel.skippedEntries().size()
        );
        metricsLabel.setForeground(UIUtil.getContextHelpForeground());
        return metricsLabel;
    }

    /**
     * 构建输出路径组件；有目录时使用超链接样式，保持状态头简洁同时保留直达入口。
     *
     * @return 输出路径组件
     */
    private JComponent buildOutputPathComponent() {
        if (viewModel.outputRootPath() == null) {
            JBLabel emptyLabel = new JBLabel("未生成输出目录");
            emptyLabel.setForeground(UIUtil.getContextHelpForeground());
            return emptyLabel;
        }
        ActionLink outputPathLink = new ActionLink(viewModel.outputRootPath().toString());
        // 输出路径保留成链接样式，既弱化视觉负担，也避免再额外放一颗重复的目录按钮。
        outputPathLink.setAutoHideOnDisable(false);
        outputPathLink.addActionListener(event -> resultActionService.openOutputDirectory(project, viewModel.outputRootPath()));
        return outputPathLink;
    }

    /**
     * 构建无边框选项卡，让过程日志和结果列表成为页面主体，而不是被额外边框再次包裹。
     *
     * @return Tab 区域
     */
    private JBTabbedPane buildTabbedPanel() {
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab("过程摘要", wrapTabContent(processConsole.getComponent()));
        tabbedPane.addTab("成功项", buildEntryTab(viewModel.successEntries(), false));
        tabbedPane.addTab("失败项", buildEntryTab(viewModel.failedEntries(), true));
        tabbedPane.addTab("跳过项", buildEntryTab(viewModel.skippedEntries(), false));
        return tabbedPane;
    }

    /**
     * 包装 Tab 内容并清空外围边距，避免内容区再出现额外盒子感。
     *
     * @param component Tab 内容
     * @return 包装后的内容
     */
    private JPanel wrapTabContent(JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder());
        component.setBorder(BorderFactory.createEmptyBorder());
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 构建结果项列表，用结构化列表替代大段文本拼接，提升结果页的扫描效率。
     *
     * @param entries 结果项
     * @param navigable 是否允许定位失败源文件
     * @return 列表组件
     */
    private JComponent buildEntryTab(List<ExportEntry> entries, boolean navigable) {
        if (entries.isEmpty()) {
            return buildEmptyState();
        }
        JBList<ExportEntry> entryList = new JBList<>(entries.toArray(ExportEntry[]::new));
        entryList.setCellRenderer(buildEntryRenderer());
        if (navigable) {
            installFailureNavigation(entryList);
        }
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder());
        if (navigable && entries.stream().anyMatch(entry -> entry.sourcePath() != null)) {
            JBLabel hintLabel = new JBLabel("双击条目可定位失败文件");
            hintLabel.setForeground(UIUtil.getContextHelpForeground());
            panel.add(hintLabel, BorderLayout.NORTH);
        }
        panel.add(ScrollPaneFactory.createScrollPane(entryList, true), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 构建空状态，避免空 Tab 再回退成孤零零的文本域。
     *
     * @return 空状态组件
     */
    private JComponent buildEmptyState() {
        JPanel emptyPanel = new JPanel(new BorderLayout());
        JBLabel emptyLabel = new JBLabel("无");
        emptyLabel.setForeground(UIUtil.getContextHelpForeground());
        emptyPanel.add(emptyLabel, BorderLayout.NORTH);
        return emptyPanel;
    }

    /**
     * 构建结果项渲染器，把输出路径和结果说明拆成两层，避免继续使用低信息密度的纯文本块。
     *
     * @return 列表渲染器
     */
    private DefaultListCellRenderer buildEntryRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                ExportEntry entry = (ExportEntry) value;
                String hintText = entry.sourcePath() != null ? "<br><span style='color:#808080'>双击定位源文件</span>" : "";
                label.setText("<html><div style='padding:4px 0'><b>"
                        + escapeHtml(entry.outputPath().toString())
                        + "</b><br>"
                        + toHtmlLines(resolveEntryMessage(entry))
                        + hintText
                        + "</div></html>");
                // 列表项保留适度留白，让多行信息仍然容易扫读，但不再需要外围卡片边框。
                label.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
                return label;
            }
        };
    }

    /**
     * 给失败项安装双击定位能力，把“查看所选失败类/文件”从底部按钮改成更自然的就地交互。
     *
     * @param entryList 失败项列表
     */
    private void installFailureNavigation(JBList<ExportEntry> entryList) {
        entryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                ExportEntry selectedEntry = entryList.getSelectedValue();
                if (selectedEntry != null && selectedEntry.sourcePath() != null) {
                    resultActionService.navigateToFailure(project, selectedEntry);
                }
            }
        });
    }

    /**
     * 创建 IDEA 原生控制台，并注册可点击日志定位过滤器。
     *
     * @param project 当前项目
     * @return 控制台视图
     */
    private ConsoleView createProcessConsole(Project project) {
        TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        builder.setViewer(true);
        builder.addFilter(new ResultLogHyperlinkFilter(project));
        ConsoleView consoleView = builder.getConsole();
        consoleView.getComponent().setBorder(BorderFactory.createEmptyBorder());
        appendProcessLogs(consoleView);
        return consoleView;
    }

    /**
     * 把过程日志按内容类型写入原生控制台，让错误和提示在视觉上立即分层。
     *
     * @param consoleView 控制台视图
     */
    private void appendProcessLogs(ConsoleView consoleView) {
        consoleView.clear();
        List<String> logLines = viewModel.processLogs().isEmpty() ? List.of("无") : viewModel.processLogs();
        for (String line : logLines) {
            consoleView.print(line + System.lineSeparator(), resolveConsoleContentType(line));
        }
    }

    /**
     * 根据日志前缀选择控制台输出样式，让关键错误不再淹没在同色纯文本里。
     *
     * @param line 日志行
     * @return 控制台内容类型
     */
    private ConsoleViewContentType resolveConsoleContentType(String line) {
        if (line.startsWith("[ERROR]")) {
            return ConsoleViewContentType.ERROR_OUTPUT;
        }
        if (line.startsWith("[WARN]") || line.startsWith("[WARNING]")) {
            return ConsoleViewContentType.LOG_WARNING_OUTPUT;
        }
        if (line.startsWith("[INFO]")) {
            return ConsoleViewContentType.LOG_INFO_OUTPUT;
        }
        if (line.startsWith("$ ")) {
            return ConsoleViewContentType.USER_INPUT;
        }
        return ConsoleViewContentType.NORMAL_OUTPUT;
    }

    /**
     * 生成主状态标题，只保留结果结论和最小必要原因，不把原始日志拉进头部。
     *
     * @return 主状态标题
     */
    private String resolveStatusTitle() {
        if (!hasFailures()) {
            return viewModel.skippedEntries().isEmpty() ? "导出成功" : "导出完成（含跳过项）";
        }
        if (viewModel.compileSummary().contains("编译")) {
            return "导出失败（编译异常）";
        }
        return "导出失败";
    }

    /**
     * 判断是否存在失败项，统一驱动头部状态图标和标题文案。
     *
     * @return 是否失败
     */
    private boolean hasFailures() {
        return !viewModel.failedEntries().isEmpty();
    }

    /**
     * 兜底结果说明，避免空消息把列表渲染成只有路径的半成品状态。
     *
     * @param entry 结果项
     * @return 展示文案
     */
    private String resolveEntryMessage(ExportEntry entry) {
        if (entry.message() != null && !entry.message().isBlank()) {
            return entry.message();
        }
        if (viewModel.failedEntries().contains(entry)) {
            return "导出失败";
        }
        if (viewModel.skippedEntries().contains(entry)) {
            return "已跳过";
        }
        return "导出成功";
    }

    /**
     * 统一转义 HTML，避免路径和错误文本中的特殊字符破坏结果项排版。
     *
     * @param text 原始文本
     * @return 转义后文本
     */
    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 把多行文本转成 HTML 换行，保证错误消息在列表中按原有层次展示。
     *
     * @param text 原始文本
     * @return HTML 文本
     */
    private String toHtmlLines(String text) {
        return escapeHtml(text).replace(System.lineSeparator(), "<br>").replace("\n", "<br>");
    }

    /**
     * 结果页控制台日志过滤器，把稳定的文件路径格式转换成 IDEA 原生可点击超链接。
     */
    private static class ResultLogHyperlinkFilter implements Filter {

        private final Project project;

        /**
         * 初始化过滤器。
         *
         * @param project 当前项目
         */
        private ResultLogHyperlinkFilter(Project project) {
            this.project = project;
        }

        /**
         * 匹配日志中的文件路径与行列信息，命中时返回超链接高亮结果。
         *
         * @param line 当前日志行
         * @param entireLength 控制台全文长度
         * @return 过滤结果
         */
        @Override
        public Result applyFilter(String line, int entireLength) {
            Optional<LogLocation> location = parseLocation(line);
            if (location.isEmpty()) {
                return null;
            }
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(location.get().path().replace('\\', '/'));
            if (virtualFile == null) {
                return null;
            }
            int lineStartOffset = entireLength - line.length();
            int highlightStart = lineStartOffset + location.get().highlightStart();
            int highlightEnd = lineStartOffset + location.get().highlightEnd();
            OpenFileHyperlinkInfo hyperlinkInfo = new OpenFileHyperlinkInfo(project, virtualFile, Math.max(location.get().line() - 1, 0), Math.max(location.get().column() - 1, 0));
            return new Result(highlightStart, highlightEnd, hyperlinkInfo);
        }

        /**
         * 从日志里解析文件路径和可选的行列号，优先支持 Maven 原始编译错误格式，其次支持 IDEA 摘要格式。
         *
         * @param line 当前日志行
         * @return 解析结果
         */
        private Optional<LogLocation> parseLocation(String line) {
            Matcher locationMatcher = WINDOWS_OR_UNIX_LOCATION_PATTERN.matcher(line);
            if (locationMatcher.find()) {
                return Optional.of(new LogLocation(
                        locationMatcher.group(1),
                        Integer.parseInt(locationMatcher.group(2)),
                        Integer.parseInt(locationMatcher.group(3)),
                        locationMatcher.start(1),
                        locationMatcher.end(3) + 1
                ));
            }
            Matcher ideaSummaryMatcher = IDEA_SUMMARY_PATTERN.matcher(line);
            if (ideaSummaryMatcher.find()) {
                return Optional.of(new LogLocation(
                        ideaSummaryMatcher.group(1),
                        1,
                        1,
                        ideaSummaryMatcher.start(1),
                        ideaSummaryMatcher.end(1)
                ));
            }
            return Optional.empty();
        }
    }

    /**
     * 控制台中命中的可定位日志位置。
     *
     * @param path 文件路径
     * @param line 行号
     * @param column 列号
     * @param highlightStart 高亮起点
     * @param highlightEnd 高亮终点
     */
    private record LogLocation(String path, int line, int column, int highlightStart, int highlightEnd) {
    }
}
