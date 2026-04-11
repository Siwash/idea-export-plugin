package com.seeyon.ideaexport.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.CompileMode;
import com.seeyon.ideaexport.model.CompileResult;
import com.seeyon.ideaexport.model.ExportRequest;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 编译服务，负责在导出前执行 Maven 当前工程编译或 IDEA 编译。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class CompileService {

    /**
     * 根据导出请求选择编译模式；允许用户关闭编译，直接导出已有产物。
     *
     * @param project 当前项目
     * @param request 导出请求
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    public CompileResult compile(@NotNull Project project, @NotNull ExportRequest request) throws ExportException {
        Set<String> moduleBasePaths = request.selectedItems().stream()
                .map(selectedItem -> selectedItem.moduleBasePath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> moduleNames = request.selectedItems().stream()
                .map(selectedItem -> selectedItem.moduleName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (request.skipCompile()) {
            return skipCompile(moduleBasePaths);
        }
        if (request.compileMode() == CompileMode.IDEA) {
            return compileWithIdea(project, moduleNames);
        }
        return compileWithMavenCurrentModule(project, moduleBasePaths);
    }

    /**
     * 跳过编译时直接返回约定输出目录，允许用户复用现有编译产物。
     *
     * @param moduleBasePaths 模块目录集合
     * @return 跳过编译结果
     */
    private CompileResult skipCompile(Set<String> moduleBasePaths) {
        Map<String, Path> outputDirectories = new LinkedHashMap<>();
        for (String moduleBasePath : moduleBasePaths) {
            Path modulePath = Path.of(moduleBasePath);
            outputDirectories.put(modulePath.getFileName().toString(), modulePath.resolve("target").resolve("classes"));
        }
        // 关闭编译开关后默认复用 Maven 产物目录，保持行为可预测。
        return CompileResult.success(List.copyOf(outputDirectories.keySet()), outputDirectories, "已跳过编译，直接使用现有产物");
    }

    /**
     * 使用 Maven 逐个编译涉及模块，避免触发整仓编译。
     *
     * @param project 当前项目
     * @param moduleBasePaths 涉及模块目录
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    protected CompileResult compileWithMavenCurrentModule(@NotNull Project project, @NotNull Set<String> moduleBasePaths) throws ExportException {
        List<String> compiledModules = new ArrayList<>();
        Map<String, Path> outputDirectories = new LinkedHashMap<>();
        for (String moduleBasePath : moduleBasePaths) {
            Path modulePath = Path.of(moduleBasePath);
            Path pomPath = modulePath.resolve("pom.xml");
            if (!Files.exists(pomPath)) {
                throw new ExportException("当前工程不是 Maven 模块，无法执行默认编译: " + moduleBasePath);
            }
            ProcessBuilder processBuilder = createMavenProcessBuilder(pomPath);
            processBuilder.directory(modulePath.toFile());
            processBuilder.redirectErrorStream(true);
            String output = executeProcess(processBuilder);
            int exitCode = resolveProcessExitCode(output);
            if (exitCode != 0) {
                throw new ExportException("Maven 编译失败\n" + summarizeProcessOutput(output));
            }
            String moduleName = modulePath.getFileName().toString();
            compiledModules.add(moduleName);
            // Maven 当前工程编译产物固定落到 target/classes，导出阶段据此读取 class 文件。
            outputDirectories.put(moduleName, modulePath.resolve("target").resolve("classes"));
        }
        return CompileResult.success(compiledModules, outputDirectories, "Maven 当前工程编译成功");
    }

    /**
     * 使用 IDEA 编译指定模块集合。
     *
     * @param project 当前项目
     * @param moduleNames 模块名集合
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    protected CompileResult compileWithIdea(@NotNull Project project, @NotNull Collection<String> moduleNames) throws ExportException {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        List<Module> targetModules = new ArrayList<>();
        for (Module module : modules) {
            if (moduleNames.contains(module.getName())) {
                targetModules.add(module);
            }
        }
        if (targetModules.isEmpty()) {
            throw new ExportException("未找到需要 IDEA 编译的模块");
        }

        CompilerManager compilerManager = CompilerManager.getInstance(project);
        CompileScope compileScope = compilerManager.createModulesCompileScope(targetModules.toArray(Module[]::new), true, true);
        AtomicReference<CompileContext> compileContextHolder = new AtomicReference<>();
        AtomicReference<Integer> errorCountHolder = new AtomicReference<>(0);
        CountDownLatch latch = new CountDownLatch(1);
        ApplicationManager.getApplication().invokeLater(() -> compilerManager.make(compileScope, (aborted, errors, warnings, compileContext) -> {
            // IDEA 编译回调是异步的，必须等待回调结束后再读取结果。
            compileContextHolder.set(compileContext);
            errorCountHolder.set(errors);
            latch.countDown();
        }));
        awaitCompileResult(latch);
        CompileContext compileContext = compileContextHolder.get();
        if (Objects.isNull(compileContext)) {
            throw new ExportException("IDEA 编译未返回结果");
        }
        if (errorCountHolder.get() > 0) {
            throw new ExportException("IDEA 编译失败\n" + summarizeIdeaMessages(compileContext, errorCountHolder.get()));
        }

        Map<String, Path> outputDirectories = new LinkedHashMap<>();
        for (Module module : targetModules) {
            CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
            VirtualFile compilerOutputPath = Objects.nonNull(compilerModuleExtension)
                    ? compilerModuleExtension.getCompilerOutputPath()
                    : null;
            if (Objects.isNull(compilerOutputPath)) {
                throw new ExportException("IDEA 编译完成但未找到模块输出目录: " + module.getName());
            }
            // IDEA 编译模式必须把真实输出目录回传给导出链路，不能继续固定读取 target/classes。
            outputDirectories.put(module.getName(), Path.of(compilerOutputPath.getPath()));
        }
        return CompileResult.success(targetModules.stream().map(Module::getName).toList(), outputDirectories, "IDEA 编译成功");
    }

    /**
     * 执行外部命令并返回全部输出。
     *
     * @param processBuilder 进程构建器
     * @return 命令输出
     * @throws ExportException 执行失败
     */
    private String executeProcess(ProcessBuilder processBuilder) throws ExportException {
        try {
            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 && output.isBlank()) {
                throw new ExportException("外部编译命令执行失败，退出码: " + exitCode);
            }
            return output + System.lineSeparator() + "__EXIT_CODE__=" + exitCode;
        } catch (IOException exception) {
            throw new ExportException("启动 Maven 编译失败，请确认系统可执行 cmd /c mvn -v，并在 IDEA 启动后可继承该环境变量", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExportException("Maven 编译被中断", exception);
        }
    }

    /**
     * 从执行输出中解析退出码，避免把成功判断绑定到固定英文文案。
     *
     * @param output 命令输出
     * @return 退出码
     */
    private int resolveProcessExitCode(String output) {
        String marker = "__EXIT_CODE__=";
        int markerIndex = output.lastIndexOf(marker);
        if (markerIndex < 0) {
            return 1;
        }
        return Integer.parseInt(output.substring(markerIndex + marker.length()).trim());
    }

    /**
     * 从长输出中提取最后几行，避免错误提示过长影响阅读。
     *
     * @param output 命令输出
     * @return 摘要文本
     */
    private String summarizeProcessOutput(String output) {
        List<String> lines = output.lines()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("__EXIT_CODE__="))
                .toList();
        int fromIndex = Math.max(lines.size() - 20, 0);
        return String.join(System.lineSeparator(), lines.subList(fromIndex, lines.size()));
    }

    /**
     * 汇总 IDEA 编译失败摘要，优先提取真实编译错误文本，避免只显示状态占位符。
     *
     * @param compileContext 编译上下文
     * @param errorCount 错误数
     * @return 可读错误摘要
     */
    String summarizeIdeaMessages(CompileContext compileContext, int errorCount) {
        String messageSummary = summarizeCompilerMessages(compileContext);
        if (!messageSummary.isBlank()) {
            // 能拿到真实编译错误时必须优先展示，不能退化成 compileContext 的状态字符串。
            return messageSummary;
        }
        String contextText = Objects.nonNull(compileContext) ? String.valueOf(compileContext).trim() : "";
        if (!contextText.isBlank() && !contextText.contains("COMPILE_SERVER_BUILD_STATUS")) {
            return contextText;
        }
        return "错误数: " + errorCount + System.lineSeparator() + "请查看 IDEA Build 窗口中的详细编译错误信息";
    }

    /**
     * 从 IDEA 编译上下文中抽取错误级消息，拼成面向用户的摘要文本。
     *
     * @param compileContext 编译上下文
     * @return 错误摘要
     */
    String summarizeCompilerMessages(CompileContext compileContext) {
        if (Objects.isNull(compileContext)) {
            return "";
        }
        CompilerMessage[] errorMessages = compileContext.getMessages(CompilerMessageCategory.ERROR);
        List<String> lines = new ArrayList<>();
        for (CompilerMessage message : errorMessages) {
            if (Objects.isNull(message)) {
                continue;
            }
            StringBuilder lineBuilder = new StringBuilder();
            VirtualFile virtualFile = message.getVirtualFile();
            if (Objects.nonNull(virtualFile)) {
                // 这里优先取稳定的文件路径，避免不同 VirtualFile 实现的展示文案不一致。
                lineBuilder.append(virtualFile.getPath()).append(" - ");
            }
            String messageText = Objects.nonNull(message.getMessage()) ? message.getMessage().trim() : "";
            if (messageText.isBlank()) {
                continue;
            }
            lineBuilder.append(messageText);
            lines.add(lineBuilder.toString());
            if (lines.size() >= 10) {
                // 错误太多时只取前十条，避免通知窗口被海量日志淹没。
                break;
            }
        }
        return String.join(System.lineSeparator(), lines);
    }

    /**
     * 在 Windows 下通过 cmd 启动 Maven，保证能解析 mvn.cmd/bat 等命令包装器。
     *
     * @param pomPath 模块 pom 路径
     * @return Maven 进程构建器
     */
    ProcessBuilder createMavenProcessBuilder(Path pomPath) {
        return new ProcessBuilder("cmd", "/c", "mvn", "-f", pomPath.toString(), "-DskipTests", "compile");
    }

    /**
     * 等待 IDEA 编译回调完成，避免主线程提前读取空结果。
     *
     * @param latch 编译完成门闩
     * @throws ExportException 等待被中断时抛出
     */
    private void awaitCompileResult(CountDownLatch latch) throws ExportException {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExportException("等待 IDEA 编译结果时被中断", exception);
        }
    }
}
