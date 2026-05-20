package com.seeyon.ideaexport.action;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.HotDeployResult;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import com.seeyon.ideaexport.resolver.SelectionResolver;
import com.seeyon.ideaexport.service.HotDeployService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * JVM Debug 热部署入口，把耗时编译和类替换放入后台任务，避免阻塞 IDEA UI。
 *
 * @Author by AI.Coding
 * @Date 2026-05-16
 */
public class HotDeployAction extends DumbAwareAction {

    private final SelectionResolver selectionResolver;
    private final HotDeployService hotDeployService;

    /**
     * 使用默认选择解析器和热部署服务初始化 Action。
     */
    public HotDeployAction() {
        this.selectionResolver = new SelectionResolver();
        this.hotDeployService = new HotDeployService();
    }

    /**
     * 解析当前选择并启动后台热部署任务。
     *
     * @param event Action 事件
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        List<SelectedItem> selectedItems;
        try {
            selectedItems = selectionResolver.resolve(project, event);
        } catch (ExportException e) {
            showNotification("未选中任何文件", e.getMessage(), NotificationType.WARNING);
            return;
        }

        List<SelectedItem> javaItems = selectedItems.stream()
                .filter(item -> item.sourceType() == SourceType.JAVA_SOURCE)
                .toList();
        if (javaItems.isEmpty()) {
            showNotification("不支持的文件类型", "请选中 Java 源文件（src/main/java 下的 .java 文件）", NotificationType.WARNING);
            return;
        }
        startHotDeployTask(project, javaItems);
    }

    /**
     * 启动可见的后台任务，显示 Maven 编译和 JVM 热部署进度。
     *
     * @param project   当前项目
     * @param javaItems 待热部署 Java 文件
     */
    private void startHotDeployTask(Project project, List<SelectedItem> javaItems) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven 编译并热部署", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("准备热部署...");
                indicator.setFraction(0.0D);
                // 编译和 JDI 热替换都可能耗时，必须在后台任务执行，避免右键菜单线程卡住整个 IDE。
                HotDeployResult result = hotDeployService.compileAndHotDeploy(project, javaItems, indicator);
                notifyHotDeployResult(result);
            }
        });
    }

    /**
     * 根据热部署结果展示通知。
     *
     * @param result 热部署结果，null 表示没有活跃 Debug 会话
     */
    private void notifyHotDeployResult(HotDeployResult result) {
        if (result == null) {
            showNotification("无活跃 Debug 会话", "请先以 Debug 模式启动应用，再使用热部署", NotificationType.WARNING);
            return;
        }
        if (result.success()) {
            showNotification("热部署成功", result.summary() + "\n会话: " + result.sessionName(), NotificationType.INFORMATION);
            return;
        }
        String detail = result.summary();
        if (!result.failedClasses().isEmpty()) {
            detail += "\n失败: " + String.join(", ", result.failedClasses());
        }
        showNotification("热部署部分失败", detail, NotificationType.WARNING);
    }

    /**
     * 根据当前上下文控制热部署菜单展示。
     *
     * @param event Action 事件
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        boolean visible = event.getProject() != null
                && (event.getData(CommonDataKeys.VIRTUAL_FILE) != null
                || event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) != null);
        event.getPresentation().setEnabledAndVisible(visible);
    }

    /**
     * 声明 update 在后台线程运行，避免平台对旧 EDT update 的警告。
     *
     * @return Action 更新线程
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 发送热部署通知。
     *
     * @param title   通知标题
     * @param content 通知内容
     * @param type    通知类型
     */
    private void showNotification(String title, String content, NotificationType type) {
        Notifications.Bus.notify(new Notification("HotDeploy", title, content, type));
    }
}
