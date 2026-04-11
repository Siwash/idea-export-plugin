package com.seeyon.ideaexport.resolver;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 选择解析服务，负责把项目树、编辑器、Git 变更列表中的选择统一归一化。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class SelectionResolver {

    /**
     * 解析当前事件中的可导出文件。
     *
     * @param project 当前项目
     * @param event Action 事件
     * @return 归一化后的选中项
     * @throws ExportException 未选中可导出文件时抛出
     */
    public List<SelectedItem> resolve(@NotNull Project project, @NotNull AnActionEvent event) throws ExportException {
        return resolve(project, event.getDataContext());
    }

    /**
     * 解析当前上下文中的可导出文件，便于测试直接构造 DataContext。
     *
     * @param project 当前项目
     * @param dataContext 数据上下文
     * @return 归一化后的选中项
     * @throws ExportException 未选中可导出文件时抛出
     */
    public List<SelectedItem> resolve(@NotNull Project project, @NotNull DataContext dataContext) throws ExportException {
        Map<String, SelectedItem> selectedItems = new LinkedHashMap<>();
        for (VirtualFile virtualFile : collectVirtualFiles(dataContext)) {
            SelectedItem selectedItem = toSelectedItem(project, virtualFile);
            if (Objects.nonNull(selectedItem)) {
                selectedItems.putIfAbsent(selectedItem.sourcePath().toString(), selectedItem);
            }
        }
        if (selectedItems.isEmpty()) {
            throw new ExportException("未选择可导出文件");
        }
        return List.copyOf(selectedItems.values());
    }

    /**
     * 提取事件中的虚拟文件列表，优先处理 Git 严格选中，再处理项目树和编辑器。
     *
     * @param event Action 事件
     * @return 虚拟文件列表
     */
    List<VirtualFile> collectVirtualFiles(AnActionEvent event) {
        return collectVirtualFiles(event.getDataContext());
    }

    /**
     * 提取上下文中的虚拟文件列表，优先处理 Git 严格选中，再处理项目树和编辑器。
     *
     * @param dataContext 数据上下文
     * @return 虚拟文件列表
     */
    List<VirtualFile> collectVirtualFiles(DataContext dataContext) {
        Change[] selectedChanges = VcsDataKeys.SELECTED_CHANGES.getData(dataContext);
        List<VirtualFile> selectedChangeFiles = Objects.nonNull(selectedChanges)
                ? collectSelectedChangeFiles(selectedChanges)
                : List.of();
        VirtualFile[] fileArray = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
        VirtualFile singleFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
        return chooseVirtualFiles(fileArray, singleFile, selectedChangeFiles);
    }

    /**
     * 按优先级选择上下文文件：有严格选中 changes 时只认它，否则再看通用文件选择。
     *
     * @param fileArray 通用多选文件
     * @param singleFile 通用单文件
     * @param selectedChangeFiles 严格选中的变更文件
     * @return 实际参与导出的文件列表
     */
    List<VirtualFile> chooseVirtualFiles(VirtualFile[] fileArray, VirtualFile singleFile, List<VirtualFile> selectedChangeFiles) {
        if (Objects.nonNull(selectedChangeFiles) && !selectedChangeFiles.isEmpty()) {
            // Changes View 场景必须只认显式选中的 changes，不能混入通用文件选择。
            return List.copyOf(selectedChangeFiles);
        }
        List<VirtualFile> virtualFiles = new ArrayList<>();
        if (Objects.nonNull(fileArray)) {
            for (VirtualFile virtualFile : fileArray) {
                if (Objects.nonNull(virtualFile)) {
                    virtualFiles.add(virtualFile);
                }
            }
        }
        if (Objects.nonNull(singleFile)) {
            virtualFiles.add(singleFile);
        }
        return List.copyOf(virtualFiles);
    }

    /**
     * 收集严格选中的变更文件，避免混入通用文件选择范围。
     *
     * @param selectedChanges 严格选中的变更
     * @return 变更文件列表
     */
    private List<VirtualFile> collectSelectedChangeFiles(Change[] selectedChanges) {
        List<VirtualFile> virtualFiles = new ArrayList<>();
        for (Change change : selectedChanges) {
            VirtualFile virtualFile = resolveChangeFile(change);
            if (Objects.nonNull(virtualFile)) {
                virtualFiles.add(virtualFile);
            }
        }
        return virtualFiles;
    }

    /**
     * 把虚拟文件转换为统一选中项；不支持类型返回 null，由上层统一过滤。
     *
     * @param project 当前项目
     * @param virtualFile 虚拟文件
     * @return 统一选中项
     * @throws ExportException 文件不属于任何模块时抛出
     */
    private SelectedItem toSelectedItem(Project project, VirtualFile virtualFile) throws ExportException {
        Module module = resolveModule(project, virtualFile);
        Path moduleBasePath = resolveModuleBasePath(module);
        String normalizedPath = normalizeSeparators(virtualFile.getPath());
        SourceType sourceType = detectSourceType(normalizedPath);
        String relativeOutputPath = buildRelativeOutputPath(normalizedPath, virtualFile.getName(), sourceType);
        return new SelectedItem(
                module.getName(),
                moduleBasePath,
                Path.of(normalizedPath),
                relativeOutputPath,
                sourceType
        );
    }

    /**
     * 根据虚拟文件定位所属模块；未命中模块时直接失败，避免导出到错误目录。
     *
     * @param project 当前项目
     * @param virtualFile 虚拟文件
     * @return 所属模块
     * @throws ExportException 模块不存在时抛出
     */
    private Module resolveModule(Project project, VirtualFile virtualFile) throws ExportException {
        Module module = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile);
        if (Objects.nonNull(module)) {
            return module;
        }
        throw new ExportException("文件未归属到任何模块: " + virtualFile.getPath());
    }

    /**
     * 解析模块根目录，保证后续编译输出和 pom 查找都基于一致路径。
     *
     * @param module 模块
     * @return 模块根目录
     * @throws ExportException 模块内容根缺失时抛出
     */
    private Path resolveModuleBasePath(Module module) throws ExportException {
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        if (contentRoots.length == 0) {
            throw new ExportException("模块缺少内容根目录: " + module.getName());
        }
        return Path.of(contentRoots[0].getPath());
    }

    /**
     * 从变更对象解析虚拟文件，兼容新增、修改和删除前后路径差异。
     *
     * @param change 变更对象
     * @return 虚拟文件，未命中时返回 null
     */
    private VirtualFile resolveChangeFile(Change change) {
        FilePath afterPath = ChangesUtil.getAfterPath(change);
        FilePath beforePath = ChangesUtil.getBeforePath(change);
        FilePath filePath = Objects.nonNull(afterPath) ? afterPath : beforePath;
        if (Objects.isNull(filePath)) {
            return null;
        }
        VirtualFile virtualFile = filePath.getVirtualFile();
        if (Objects.nonNull(virtualFile)) {
            return virtualFile;
        }
        return LocalFileSystem.getInstance().findFileByPath(filePath.getPath());
    }

    /**
     * 识别源文件类型，只覆盖 spec 定义的 Java 源码和 webapp 资源。
     *
     * @param normalizedPath 归一化路径
     * @return 源文件类型
     */
    SourceType detectSourceType(String normalizedPath) {
        if (normalizedPath.contains("/src/main/java/")) {
            return SourceType.JAVA_SOURCE;
        }
        if (normalizedPath.contains("/webapp/")) {
            return SourceType.WEB_RESOURCE;
        }
        return SourceType.UNSUPPORTED;
    }

    /**
     * 计算导出相对路径，Java 源码会转成 classes 结构，web 资源保留原相对路径。
     *
     * @param normalizedPath 归一化路径
     * @param fileName 文件名
     * @param sourceType 源文件类型
     * @return 导出相对路径
     * @throws ExportException 路径无法映射时抛出
     */
    String buildRelativeOutputPath(String normalizedPath, String fileName, SourceType sourceType) throws ExportException {
        if (SourceType.JAVA_SOURCE == sourceType) {
            String relativePath = normalizedPath.substring(normalizedPath.indexOf("/src/main/java/") + "/src/main/java/".length());
            int extensionIndex = relativePath.lastIndexOf('.');
            if (extensionIndex < 0) {
                throw new ExportException("Java 文件缺少扩展名: " + normalizedPath);
            }
            return relativePath.substring(0, extensionIndex) + ".class";
        }
        if (SourceType.WEB_RESOURCE == sourceType) {
            return normalizedPath.substring(normalizedPath.indexOf("/webapp/") + "/webapp/".length());
        }
        // 不支持类型也保留文件名，便于结果面板明确提示被跳过的是哪个文件。
        return fileName;
    }

    /**
     * 统一把路径分隔符转成正斜杠，避免 Windows 下字符串判断失效。
     *
     * @param path 原始路径
     * @return 归一化路径
     */
    private String normalizeSeparators(String path) {
        return path.replace('\\', '/');
    }
}
