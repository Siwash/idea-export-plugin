package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.ExportEntry;
import com.seeyon.ideaexport.model.ExportEntryStatus;
import com.seeyon.ideaexport.model.ExportSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 文件导出执行服务，负责创建目录、复制文件并汇总执行结果。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class FileExportService {

    /**
     * 执行导出计划并汇总结果。
     *
     * @param entries 导出计划
     * @return 导出结果汇总
     * @throws ExportException 导出计划为空时抛出
     */
    public ExportSummary export(List<ExportEntry> entries) throws ExportException {
        return export(entries, new SilentExportRuntimeReporter());
    }

    /**
     * 执行导出计划并上报复制阶段进度。
     *
     * @param entries 导出计划
     * @param reporter 过程上报器
     * @return 导出结果汇总
     * @throws ExportException 导出计划为空时抛出
     */
    public ExportSummary export(List<ExportEntry> entries, ExportRuntimeReporter reporter) throws ExportException {
        Objects.requireNonNull(entries, "entries cannot be null");
        if (entries.isEmpty()) {
            throw new ExportException("没有可执行的导出项");
        }

        List<ExportEntry> finalEntries = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int entryIndex = 0;

        for (ExportEntry entry : entries) {
            entryIndex++;
            reporter.updateProgress(0.7D + (0.25D * entryIndex / Math.max(entries.size(), 1)));
            reporter.appendLog("[EXPORT] " + entry.outputPath());
            List<ExportEntry> exportedEntries = exportSingle(entry);
            for (ExportEntry exportedEntry : exportedEntries) {
                finalEntries.add(exportedEntry);
                if (exportedEntry.status() == ExportEntryStatus.EXPORTED) {
                    successCount++;
                } else if (exportedEntry.status() == ExportEntryStatus.SKIPPED) {
                    skippedCount++;
                } else {
                    failedCount++;
                }
            }
        }
        return new ExportSummary(List.copyOf(finalEntries), successCount, failedCount, skippedCount);
    }

    /**
     * 导出单个条目；类文件场景会自动包含内部类与匿名类。
     *
     * @param entry 导出项
     * @return 实际导出结果列表
     */
    private List<ExportEntry> exportSingle(ExportEntry entry) {
        if (entry.status() == ExportEntryStatus.SKIPPED) {
            return List.of(entry);
        }
        if (!Files.exists(entry.sourcePath())) {
            // 编译产物不存在时直接标记失败，避免静默生成空补丁目录。
            return List.of(entry.withStatus(ExportEntryStatus.FAILED, "源文件不存在: " + entry.sourcePath()));
        }

        if (!entry.sourcePath().getFileName().toString().endsWith(".class")) {
            return List.of(copyFile(entry.sourcePath(), entry.outputPath(), entry));
        }

        List<Path> compiledFiles = collectRelatedClassFiles(entry.sourcePath());
        List<ExportEntry> exportedEntries = new ArrayList<>();
        for (Path compiledFile : compiledFiles) {
            Path siblingOutput = entry.outputPath().resolveSibling(compiledFile.getFileName().toString());
            exportedEntries.add(copyFile(compiledFile, siblingOutput, entry));
        }
        return exportedEntries;
    }

    /**
     * 收集同名类和内部类编译产物，避免匿名类遗漏导致运行时缺类。
     *
     * @param sourceClassFile 顶层类文件路径
     * @return 相关 class 文件列表
     */
    private List<Path> collectRelatedClassFiles(Path sourceClassFile) {
        String fileName = sourceClassFile.getFileName().toString();
        String classPrefix = fileName.substring(0, fileName.length() - ".class".length());
        List<Path> classFiles = new ArrayList<>();
        try (var stream = Files.list(sourceClassFile.getParent())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String siblingName = path.getFileName().toString();
                        return siblingName.equals(classPrefix + ".class") || siblingName.startsWith(classPrefix + "$");
                    })
                    .sorted()
                    .forEach(classFiles::add);
        } catch (IOException exception) {
            // 目录扫描失败时至少回退导出顶层类文件，避免全部中断。
            classFiles.add(sourceClassFile);
        }
        if (classFiles.isEmpty()) {
            classFiles.add(sourceClassFile);
        }
        return classFiles;
    }

    /**
     * 执行单文件复制，并把异常转换为结果状态。
     *
     * @param sourcePath 源文件
     * @param outputPath 目标文件
     * @param prototype 原始导出项
     * @return 执行结果
     */
    private ExportEntry copyFile(Path sourcePath, Path outputPath, ExportEntry prototype) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.copy(sourcePath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            return new ExportEntry(prototype.moduleName(), sourcePath, outputPath, ExportEntryStatus.EXPORTED, "导出成功", -1, -1);
        } catch (IOException exception) {
            return new ExportEntry(prototype.moduleName(), sourcePath, outputPath, ExportEntryStatus.FAILED, exception.getMessage(), -1, -1);
        }
    }
}
