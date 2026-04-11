package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import com.seeyon.ideaexport.model.ExportMode;
import com.seeyon.ideaexport.model.ExportRequest;
import com.seeyon.ideaexport.model.ModulePackagingInfo;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 导出目录规划服务，负责把选中项映射为普通补丁或 bug jar 的目标路径。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class PatchLayoutService {

    /**
     * 根据导出模式、编译结果和打包信息生成完整导出计划。
     *
     * @param request 导出请求
     * @param compileResult 编译结果
     * @param packagingInfo 模块打包信息
     * @return 导出项列表
     * @throws ExportException 目录规划失败
     */
    public List<ExportEntry> plan(ExportRequest request, CompileResult compileResult, Map<String, ModulePackagingInfo> packagingInfo) throws ExportException {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(compileResult, "compileResult cannot be null");
        Objects.requireNonNull(packagingInfo, "packagingInfo cannot be null");

        List<ExportEntry> entries = new ArrayList<>();
        for (SelectedItem selectedItem : request.selectedItems()) {
            entries.add(planSingle(request, compileResult, packagingInfo, selectedItem));
        }
        return List.copyOf(entries);
    }

    /**
     * 规划单个选中项的目标路径，确保不同模式映射规则集中在一个入口。
     *
     * @param request 导出请求
     * @param compileResult 编译结果
     * @param packagingInfo 模块打包信息
     * @param selectedItem 选中项
     * @return 导出项
     * @throws ExportException 当前文件无法映射时抛出
     */
    private ExportEntry planSingle(ExportRequest request, CompileResult compileResult, Map<String, ModulePackagingInfo> packagingInfo, SelectedItem selectedItem) throws ExportException {
        if (selectedItem.sourceType() == SourceType.UNSUPPORTED) {
            // 不支持类型在规划阶段直接标记跳过，避免整个导出流程因此中断。
            return new ExportEntry(
                    selectedItem.moduleName(),
                    selectedItem.sourcePath(),
                    request.targetPath().resolve(selectedItem.relativePath()),
                    ExportEntryStatus.SKIPPED,
                    "不支持的文件类型: " + selectedItem.sourcePath()
            );
        }
        Path exportSourcePath = resolveExportSourcePath(selectedItem, compileResult);
        Path outputPath = request.mode() == ExportMode.BUG_JAR
                ? resolveBugJarOutputPath(request.targetPath(), selectedItem, packagingInfo)
                : resolveStandardOutputPath(request.targetPath(), selectedItem);
        return ExportEntry.pending(selectedItem.moduleName(), exportSourcePath, outputPath);
    }

    /**
     * 根据编译模式解析真实导出源路径，保证 IDEA 编译和 Maven 编译都从正确目录取 class 文件。
     *
     * @param selectedItem 选中项
     * @param compileResult 编译结果
     * @return 实际导出源路径
     * @throws ExportException 缺少输出目录时抛出
     */
    private Path resolveExportSourcePath(SelectedItem selectedItem, CompileResult compileResult) throws ExportException {
        if (selectedItem.isWebResource()) {
            return selectedItem.sourcePath();
        }
        Path outputDirectory = compileResult.moduleOutputDirectories().get(selectedItem.moduleName());
        if (Objects.isNull(outputDirectory)) {
            throw new ExportException("缺少模块编译输出目录: " + selectedItem.moduleName());
        }
        // 编译结果里保存的输出目录是导出类文件的唯一可信来源，不能再硬编码 target/classes。
        return outputDirectory.resolve(selectedItem.relativePath());
    }

    /**
     * 计算普通补丁模式的输出路径。
     *
     * @param targetPath 导出根目录
     * @param selectedItem 选中项
     * @return 目标输出路径
     * @throws ExportException 文件类型不支持时抛出
     */
    private Path resolveStandardOutputPath(Path targetPath, SelectedItem selectedItem) throws ExportException {
        Path seeyonRoot = targetPath.resolve("seeyon");
        if (selectedItem.isJavaSource()) {
            // 普通类补丁必须落到 seeyon/WEB-INF/classes，才能匹配正式环境类加载目录。
            return seeyonRoot.resolve("WEB-INF").resolve("classes").resolve(selectedItem.relativePath());
        }
        if (selectedItem.isWebResource()) {
            // webapp 资源继续直接映射到 seeyon 根下，保持与正式目录结构一致。
            return seeyonRoot.resolve(selectedItem.relativePath());
        }
        throw new ExportException("不支持的文件类型: " + selectedItem.sourcePath());
    }

    /**
     * 计算 bug jar 模式的输出路径。
     *
     * @param targetPath 导出根目录
     * @param selectedItem 选中项
     * @param packagingInfo 模块打包信息
     * @return 目标输出路径
     * @throws ExportException jar 名无法解析或文件类型不支持时抛出
     */
    private Path resolveBugJarOutputPath(Path targetPath, SelectedItem selectedItem, Map<String, ModulePackagingInfo> packagingInfo) throws ExportException {
        ModulePackagingInfo modulePackagingInfo = packagingInfo.get(selectedItem.moduleName());
        if (Objects.isNull(modulePackagingInfo)) {
            throw new ExportException("模块缺少 bug jar 打包信息: " + selectedItem.moduleName());
        }
        if (!selectedItem.isJavaSource()) {
            throw new ExportException("bug jar 模式仅支持类文件导出: " + selectedItem.sourcePath());
        }
        // bug jar 补丁必须放到 seeyon/WEB-INF/lib/<jar目录> 下，并直接保留类的包路径。
        return targetPath.resolve("seeyon")
                .resolve("WEB-INF")
                .resolve("lib")
                .resolve(modulePackagingInfo.jarDirectoryName())
                .resolve(selectedItem.relativePath());
    }
}
