package com.seeyon.ideaexport.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.NotNull;

/**
 * 编辑器右键导出入口，仅在当前编辑器存在文件时显示。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class EditorExportAction extends AbstractExportToSeeyonAction {

    /**
     * 判断编辑器上下文是否存在当前文件。
     *
     * @param event Action 事件
     * @return true 表示编辑器可导出
     */
    @Override
    protected boolean isContextSupported(@NotNull AnActionEvent event) {
        return event.getData(CommonDataKeys.VIRTUAL_FILE) != null;
    }
}
