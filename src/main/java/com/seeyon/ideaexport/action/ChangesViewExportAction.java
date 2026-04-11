package com.seeyon.ideaexport.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import org.jetbrains.annotations.NotNull;

/**
 * Git 变更列表右键导出入口，仅在存在严格选中变更时显示。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class ChangesViewExportAction extends AbstractExportToSeeyonAction {

    /**
     * 判断变更列表上下文是否存在严格选中的变更项。
     *
     * @param event Action 事件
     * @return true 表示变更列表可导出
     */
    @Override
    protected boolean isContextSupported(@NotNull AnActionEvent event) {
        // Git 入口必须只认显式选中的 changes，不能回退到整个变更集合。
        return event.getData(VcsDataKeys.SELECTED_CHANGES) != null;
    }
}
