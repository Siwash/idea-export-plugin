package com.seeyon.ideaexport.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.seeyon.ideaexport.model.ExportEntry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 结果窗口动作服务，负责打开目录和定位失败来源。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
public class ResultActionService {

    /**
     * 打开导出目录对应的文件夹位置。
     *
     * @param project 当前项目
     * @param seeyonRootPath seeyon 根目录
     */
    public void openOutputDirectory(Project project, @NotNull Path seeyonRootPath) {
        try {
            Path existingPath = resolveExistingDirectory(seeyonRootPath);
            java.awt.Desktop.getDesktop().open(existingPath.toFile());
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException exception) {
            // 打开目录失败时要保留结果窗口上下文，并明确提示失败原因。
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, exception.getMessage(), "打开导出目录失败"));
        }
    }

    /**
     * 尝试定位失败项的来源文件；拿不到文件时回退到错误提示。
     *
     * @param project 当前项目
     * @param entry 失败项
     */
    public void navigateToFailure(Project project, @NotNull ExportEntry entry) {
        if (Objects.isNull(entry.sourcePath())) {
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, entry.message(), "无法定位失败位置"));
            return;
        }
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(entry.sourcePath().toString().replace('\\', '/'));
        if (Objects.isNull(virtualFile)) {
            // 拿不到原始文件时至少要让用户看到失败摘要，不能静默无响应。
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project, entry.message(), "无法定位失败位置"));
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> new OpenFileDescriptor(project, virtualFile, Math.max(entry.line() - 1, 0), Math.max(entry.column() - 1, 0)).navigate(true));
    }

    /**
     * 找到最近存在的目录；seeyon 根目录尚未落盘时回退到最近已存在的父目录。
     *
     * @param targetPath 期望打开的目录
     * @return 可实际打开的目录
     * @throws IOException 没有任何可打开目录时抛出
     */
    private Path resolveExistingDirectory(Path targetPath) throws IOException {
        Path currentPath = targetPath;
        while (Objects.nonNull(currentPath) && !java.nio.file.Files.exists(currentPath)) {
            currentPath = currentPath.getParent();
        }
        if (Objects.isNull(currentPath)) {
            throw new IOException("没有可打开的导出目录: " + targetPath);
        }
        return currentPath;
    }
}
