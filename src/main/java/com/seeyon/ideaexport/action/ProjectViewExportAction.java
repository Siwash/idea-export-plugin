package com.seeyon.ideaexport.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.NotNull;

/**
 * 项目树右键导出入口，仅在存在选中文件时显示。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class ProjectViewExportAction extends AbstractExportToSeeyonAction {

    /**
     * 判断项目树上下文中是否存在可导出的文件数组。
     *
     * @param event Action 事件
     * @return true 表示项目树可导出
     */
    @Override
    protected boolean isContextSupported(@NotNull AnActionEvent event) {
        return event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) != null;
    }
}
