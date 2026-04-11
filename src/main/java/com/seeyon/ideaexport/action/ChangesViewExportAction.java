package com.seeyon.ideaexport.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vcs.VcsDataKeys;
import org.jetbrains.annotations.NotNull;

/**
 * Git 变更列表右键导出入口，兼容显式变更选择和提交区文件列表上下文。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class ChangesViewExportAction extends AbstractExportToSeeyonAction {

    /**
     * 判断变更列表上下文是否存在可导出的 Git 文件选择。
     *
     * @param event Action 事件
     * @return true 表示变更列表可导出
     */
    @Override
    protected boolean isContextSupported(@NotNull AnActionEvent event) {
        return hasSupportedContext(event.getDataContext());
    }

    /**
     * 提交区常只给通用文件数组，不一定给 SELECTED_CHANGES，因此这里要兼容两类上下文。
     *
     * @param event Action 事件
     * @return true 表示存在 Git 入口可用的文件选择
     */
    boolean hasSupportedContext(@NotNull AnActionEvent event) {
        return hasSupportedContext(event.getDataContext());
    }

    /**
     * 基于数据上下文判断 Git 入口是否可见，便于测试覆盖不同 UI 容器给出的 DataKey 组合。
     *
     * @param dataContext 数据上下文
     * @return true 表示存在 Git 入口可用的文件选择
     */
    boolean hasSupportedContext(@NotNull com.intellij.openapi.actionSystem.DataContext dataContext) {
        if (VcsDataKeys.SELECTED_CHANGES.getData(dataContext) != null) {
            return true;
        }
        // 左上角提交/当前变更文件列表通常直接提供 VirtualFile 选择，这里必须兼容，否则菜单会被错误隐藏。
        return CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext) != null || CommonDataKeys.VIRTUAL_FILE.getData(dataContext) != null;
    }
}
