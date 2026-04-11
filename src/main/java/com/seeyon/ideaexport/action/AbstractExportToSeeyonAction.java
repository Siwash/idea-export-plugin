package com.seeyon.ideaexport.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.service.ExportCommandService;
import org.jetbrains.annotations.NotNull;

/**
 * 导出到 seeyon 公共 Action 基类，统一封装项目判空和命令分发逻辑。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public abstract class AbstractExportToSeeyonAction extends DumbAwareAction {

    private final ExportCommandService exportCommandService;

    /**
     * 使用默认导出命令服务初始化 Action。
     */
    protected AbstractExportToSeeyonAction() {
        this(new ExportCommandService());
    }

    /**
     * 使用指定导出命令服务初始化 Action，便于测试替换依赖。
     *
     * @param exportCommandService 导出命令服务
     */
    protected AbstractExportToSeeyonAction(ExportCommandService exportCommandService) {
        this.exportCommandService = exportCommandService;
    }

    /**
     * 触发导出主流程；没有项目上下文时直接忽略。
     *
     * @param event Action 事件
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        exportCommandService.startExport(project, event);
    }

    /**
     * 根据当前入口是否存在项目和可导出上下文控制菜单状态。
     *
     * @param event Action 事件
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        boolean visible = event.getProject() != null && isContextSupported(event);
        event.getPresentation().setEnabledAndVisible(visible);
    }

    /**
     * 显式声明 Action 更新线程，兼容 IntelliJ 新版对 OLD_EDT 的废弃限制。
     *
     * @return 使用后台线程执行 update
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // 当前 update 只读取 DataKey，不依赖 Swing 组件状态，使用 BGT 可避免新版平台废弃告警。
        return ActionUpdateThread.BGT;
    }

    /**
     * 判断当前入口上下文是否允许展示导出菜单。
     *
     * @param event Action 事件
     * @return true 表示支持
     */
    protected abstract boolean isContextSupported(@NotNull AnActionEvent event);
}
