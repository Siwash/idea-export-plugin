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
import com.seeyon.ideaexport.model.CompileStrategy;
import com.seeyon.ideaexport.model.ExportRequest;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 编译服务，负责在导出前执行 Maven 当前工程编译或 IDEA 编译。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
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
        return compile(project, request, new SilentExportRuntimeReporter());
    }

    /**
     * 根据导出请求选择编译模式，并把过程实时上报给过程窗口。
     *
     * @param project 当前项目
     * @param request 导出请求
     * @param reporter 过程上报器
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    public CompileResult compile(@NotNull Project project, @NotNull ExportRequest request, @NotNull ExportRuntimeReporter reporter) throws ExportException {
        List<Path> orderedModuleBasePaths = resolveOrderedModuleBasePaths(request);
        Set<String> moduleNames = request.selectedItems().stream()
                .map(selectedItem -> selectedItem.moduleName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (request.mode().isSourceExport()) {
            reporter.updateStage("源码导出无需编译，直接复制原文件");
            reporter.appendLog("[SOURCE] 源码导出模式无需编译，直接复制原文件");
            // 源码模式直接复用主流程后半段，统一交给路径规划与复制阶段处理。
            return CompileResult.success(List.of(), Map.of(), "源码导出无需编译");
        }
        if (request.skipCompile()) {
            reporter.updateStage("已跳过编译，直接使用现有产物");
            reporter.appendLog("[SKIP] 跳过编译，直接使用现有产物");
            return skipCompile(orderedModuleBasePaths);
        }
        if (request.compileMode() == CompileMode.IDEA) {
            reporter.updateStage("正在执行 IDEA 编译...");
            reporter.appendLog("[IDEA] 开始编译选中模块");
            return compileWithIdea(project, moduleNames, reporter);
        }
        if (request.compileStrategy() == CompileStrategy.PARALLEL) {
            reporter.updateStage("正在执行 Maven 并行安装...");
            reporter.appendLog("[MAVEN] 编译策略: 并行多线程编译");
            return compileWithMavenParallel(project, orderedModuleBasePaths, reporter);
        }
        reporter.updateStage("正在执行 Maven 串行安装...");
        reporter.appendLog("[MAVEN] 编译策略: 串行编译");
        return compileWithMavenSerial(project, orderedModuleBasePaths, reporter);
    }

    /**
     * 解析模块目录顺序；用户未显式调整时退回当前选中文件首次出现顺序。
     *
     * @param request 导出请求
     * @return 有序模块目录列表
     */
    private List<Path> resolveOrderedModuleBasePaths(@NotNull ExportRequest request) {
        if (!request.orderedModuleBasePaths().isEmpty()) {
            return List.copyOf(request.orderedModuleBasePaths());
        }
        return request.selectedItems().stream()
                .map(SelectedItem -> SelectedItem.moduleBasePath())
                .distinct()
                .toList();
    }

    /**
     * 跳过编译时直接返回约定输出目录，允许用户复用现有编译产物。
     *
     * @param orderedModuleBasePaths 模块目录列表
     * @return 跳过编译结果
     */
    private CompileResult skipCompile(List<Path> orderedModuleBasePaths) {
        Map<String, Path> outputDirectories = new LinkedHashMap<>();
        for (Path modulePath : orderedModuleBasePaths) {
            outputDirectories.put(modulePath.getFileName().toString(), modulePath.resolve("target").resolve("classes"));
        }
        // 关闭编译开关后默认复用 Maven 产物目录，保持行为可预测。
        return CompileResult.success(List.copyOf(outputDirectories.keySet()), outputDirectories, "已跳过编译，直接使用现有产物");
    }

    /**
     * 兼容旧测试入口，默认走串行 Maven 安装。
     *
     * @param project 当前项目
     * @param moduleBasePaths 涉及模块目录
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    protected CompileResult compileWithMavenCurrentModule(@NotNull Project project, @NotNull Set<String> moduleBasePaths) throws ExportException {
        return compileWithMavenSerial(project, moduleBasePaths.stream().map(Path::of).toList(), new SilentExportRuntimeReporter());
    }

    /**
     * 串行执行 Maven install，保证后续模块可以消费前面模块写入本地仓库的依赖。
     *
     * @param project 当前项目
     * @param orderedModuleBasePaths 涉及模块目录
     * @param reporter 过程上报器
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    protected CompileResult compileWithMavenSerial(@NotNull Project project, @NotNull List<Path> orderedModuleBasePaths, @NotNull ExportRuntimeReporter reporter) throws ExportException {
        validateMavenModules(orderedModuleBasePaths);
        List<String> compiledModules = new ArrayList<>();
        Map<String, Path> outputDirectories = new LinkedHashMap<>();
        int moduleIndex = 0;
        for (Path modulePath : orderedModuleBasePaths) {
            moduleIndex++;
            String moduleName = modulePath.getFileName().toString();
            reporter.updateStage("正在串行安装模块（" + moduleIndex + "/" + Math.max(orderedModuleBasePaths.size(), 1) + "）：" + moduleName);
            reporter.updateProgress(0.1D + (0.25D * moduleIndex / Math.max(orderedModuleBasePaths.size(), 1)));
            compileSingleMavenModule(modulePath, reporter);
            compiledModules.add(moduleName);
            outputDirectories.put(moduleName, modulePath.resolve("target").resolve("classes"));
        }
        return CompileResult.success(compiledModules, outputDirectories, "Maven 串行安装成功");
    }

    /**
     * 并行执行 Maven install，在没有强依赖链时提升多模块构建速度。
     *
     * @param project 当前项目
     * @param orderedModuleBasePaths 涉及模块目录
     * @param reporter 过程上报器
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    protected CompileResult compileWithMavenParallel(@NotNull Project project, @NotNull List<Path> orderedModuleBasePaths, @NotNull ExportRuntimeReporter reporter) throws ExportException {
        validateMavenModules(orderedModuleBasePaths);
        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(Math.max(orderedModuleBasePaths.size(), 1), 4));
        List<Future<ModuleCompileOutcome>> futures = new ArrayList<>();
        for (Path modulePath : orderedModuleBasePaths) {
            futures.add(executorService.submit(new MavenInstallTask(modulePath, reporter)));
        }

        List<String> compiledModules = new ArrayList<>();
        Map<String, Path> outputDirectories = new LinkedHashMap<>();
        List<String> failureSummaries = new ArrayList<>();
        int completedCount = 0;
        try {
            for (Future<ModuleCompileOutcome> future : futures) {
                try {
                    ModuleCompileOutcome outcome = future.get();
                    completedCount++;
                    reporter.updateStage("正在执行 Maven 并行安装（" + completedCount + "/" + orderedModuleBasePaths.size() + "）");
                    reporter.updateProgress(0.1D + (0.25D * completedCount / Math.max(orderedModuleBasePaths.size(), 1)));
                    compiledModules.add(outcome.moduleName());
                    outputDirectories.put(outcome.moduleName(), outcome.outputDirectory());
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof ExportException exportException) {
                        failureSummaries.add(exportException.getMessage());
                        reporter.appendLog("[MAVEN] 并行编译失败，已停止收集结果");
                    } else {
                        failureSummaries.add("并行 Maven 编译失败: " + exception.getMessage());
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExportException("等待并行 Maven 编译结果时被中断", exception);
        } finally {
            executorService.shutdownNow();
        }

        if (!failureSummaries.isEmpty()) {
            throw new ExportException("Maven 并行安装失败\n" + String.join(System.lineSeparator(), failureSummaries));
        }
        return CompileResult.success(compiledModules, outputDirectories, "Maven 并行安装成功");
    }

    /**
     * 校验所有模块都是可执行的 Maven 工程，避免运行到一半才发现缺 pom 文件。
     *
     * @param orderedModuleBasePaths 模块目录列表
     * @throws ExportException 模块非法时抛出
     */
    private void validateMavenModules(List<Path> orderedModuleBasePaths) throws ExportException {
        for (Path modulePath : orderedModuleBasePaths) {
            if (!Files.exists(modulePath.resolve("pom.xml"))) {
                throw new ExportException("当前工程不是 Maven 模块，无法执行默认编译: " + modulePath);
            }
        }
    }

    /**
     * 编译单个 Maven 模块，并把真实命令和输出按模块前缀写入日志。
     *
     * @param modulePath 模块目录
     * @param reporter 过程上报器
     * @throws ExportException 编译失败
     */
    private void compileSingleMavenModule(Path modulePath, ExportRuntimeReporter reporter) throws ExportException {
        String moduleName = modulePath.getFileName().toString();
        Path pomPath = modulePath.resolve("pom.xml");
        ProcessBuilder processBuilder = createMavenProcessBuilder(pomPath);
        processBuilder.directory(modulePath.toFile());
        processBuilder.redirectErrorStream(true);
        applyMavenProcessEncoding(processBuilder);
        reporter.appendLog("$ [" + moduleName + "] " + String.join(" ", processBuilder.command()));
        reporter.appendLog("[" + moduleName + "] [START] 开始执行 Maven install");
        String output = executeProcess(processBuilder, reporter, moduleName);
        int exitCode = resolveProcessExitCode(output);
        if (exitCode != 0) {
            throw new ExportException("模块 " + moduleName + " Maven 安装失败\n" + summarizeProcessOutput(output));
        }
        reporter.appendLog("[" + moduleName + "] [SUCCESS] Maven install 完成");
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
        return compileWithIdea(project, moduleNames, new SilentExportRuntimeReporter());
    }

    /**
     * 使用 IDEA 编译指定模块集合，并回传阶段和结果文本。
     *
     * @param project 当前项目
     * @param moduleNames 模块名集合
     * @param reporter 过程上报器
     * @return 编译结果
     * @throws ExportException 编译失败
     */
    protected CompileResult compileWithIdea(@NotNull Project project, @NotNull Collection<String> moduleNames, @NotNull ExportRuntimeReporter reporter) throws ExportException {
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

        reporter.appendLog("[IDEA] 编译模块: " + String.join(", ", targetModules.stream().map(Module::getName).toList()));
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        CompileScope compileScope = compilerManager.createModulesCompileScope(targetModules.toArray(Module[]::new), true, true);
        AtomicReference<CompileContext> compileContextHolder = new AtomicReference<>();
        AtomicReference<Integer> errorCountHolder = new AtomicReference<>(0);
        CountDownLatch latch = new CountDownLatch(1);
        ApplicationManager.getApplication().invokeAndWait(() -> {
            // JetBrains 2024.2 要求 CompilerManager.make 从 EDT 发起，否则会直接抛 EDT 访问异常。
            compilerManager.make(compileScope, (aborted, errors, warnings, compileContext) -> {
                // IDEA 编译回调是异步的，必须等待回调结束后再读取结果。
                compileContextHolder.set(compileContext);
                errorCountHolder.set(errors);
                reporter.updateStage(errors > 0 ? "IDEA 编译失败" : "IDEA 编译完成");
                reporter.appendLog(errors > 0 ? "[IDEA] 编译失败，错误数: " + errors : "[IDEA] 编译成功");
                reporter.updateProgress(0.35D);
                latch.countDown();
            });
        });
        awaitCompileResult(latch);
        CompileContext compileContext = compileContextHolder.get();
        if (Objects.isNull(compileContext)) {
            throw new ExportException("IDEA 编译未返回结果");
        }
        if (errorCountHolder.get() > 0) {
            CompilerMessage firstErrorMessage = resolveFirstCompilerErrorMessage(compileContext);
            java.nio.file.Path failedSourcePath = resolveCompilerErrorSource(firstErrorMessage);
            String summary = "IDEA 编译失败\n" + summarizeIdeaMessages(compileContext, errorCountHolder.get());
            if (Objects.nonNull(failedSourcePath)) {
                throw new ExportException(summary, failedSourcePath, resolveCompilerErrorLine(firstErrorMessage), resolveCompilerErrorColumn(firstErrorMessage));
            }
            throw new ExportException(summary);
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
        return executeProcess(processBuilder, new SilentExportRuntimeReporter(), null);
    }

    /**
     * 执行外部命令并把输出逐行上报给过程窗口。
     *
     * @param processBuilder 进程构建器
     * @param reporter 过程上报器
     * @param moduleName 模块名
     * @return 命令输出
     * @throws ExportException 执行失败
     */
    private String executeProcess(ProcessBuilder processBuilder, ExportRuntimeReporter reporter, String moduleName) throws ExportException {
        try {
            Process process = processBuilder.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), resolveProcessCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    reporter.appendLog(formatLogLine(moduleName, line));
                }
            }
            String output = String.join(System.lineSeparator(), lines);
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
     * 为并发输出统一补模块前缀，避免多个模块日志交错后无法阅读。
     *
     * @param moduleName 模块名
     * @param line 原始日志
     * @return 带前缀日志
     */
    private String formatLogLine(String moduleName, String line) {
        if (moduleName == null || moduleName.isBlank()) {
            return line;
        }
        return "[" + moduleName + "] " + line;
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
     * 提取第一条编译错误对应的源文件，给结果窗口提供最小可用的失败定位能力。
     *
     * @param compileContext 编译上下文
     * @return 第一条错误来源文件
     */
    private CompilerMessage resolveFirstCompilerErrorMessage(CompileContext compileContext) {
        if (Objects.isNull(compileContext)) {
            return null;
        }
        CompilerMessage[] errorMessages = compileContext.getMessages(CompilerMessageCategory.ERROR);
        for (CompilerMessage message : errorMessages) {
            if (Objects.isNull(message)) {
                continue;
            }
            return message;
        }
        return null;
    }

    /**
     * 从编译错误消息中提取来源文件，给结果窗口提供最小可用的失败定位能力。
     *
     * @param compilerMessage 编译错误消息
     * @return 错误来源文件
     */
    private java.nio.file.Path resolveCompilerErrorSource(CompilerMessage compilerMessage) {
        if (Objects.isNull(compilerMessage) || Objects.isNull(compilerMessage.getVirtualFile())) {
            return null;
        }
        return java.nio.file.Path.of(compilerMessage.getVirtualFile().getPath());
    }

    /**
     * 解析编译错误行号；当前平台拿不到时返回 -1。
     *
     * @param compilerMessage 编译错误消息
     * @return 错误行号
     */
    private int resolveCompilerErrorLine(CompilerMessage compilerMessage) {
        return -1;
    }

    /**
     * 解析编译错误列号；当前平台拿不到时返回 -1。
     *
     * @param compilerMessage 编译错误消息
     * @return 错误列号
     */
    private int resolveCompilerErrorColumn(CompilerMessage compilerMessage) {
        return -1;
    }

    /**
     * 在 Windows 下通过 cmd 启动 Maven，保证能解析 mvn.cmd/bat 等命令包装器。
     *
     * @param pomPath 模块 pom 路径
     * @return Maven 进程构建器
     */
    ProcessBuilder createMavenProcessBuilder(Path pomPath) {
        return createMavenProcessBuilder(pomPath, "install");
    }

    /**
     * 通过 cmd 启动 Maven 执行指定 goal，由调用方控制生命周期。
     *
     * @param pomPath 模块 pom 路径
     * @param goal    Maven goal
     * @return Maven 进程构建器
     */
    ProcessBuilder createMavenProcessBuilder(Path pomPath, String goal) {
        return new ProcessBuilder("cmd", "/c", "mvn", "-f", pomPath.toString(), "-DskipTests", goal);
    }

    /**
     * 仅运行 compile 阶段，比 install 更快，供热部署使用。
     *
     * @param modulePath 模块目录
     * @throws ExportException 编译失败
     */
    public void compileForHotDeploy(Path modulePath) throws ExportException {
        compileForHotDeploy(modulePath, null);
    }

    /**
     * 仅运行 compile 阶段，并同步更新后台任务进度。
     *
     * @param modulePath 模块目录
     * @param indicator  进度指示器，允许为空
     * @throws ExportException 编译失败
     */
    public void compileForHotDeploy(Path modulePath, com.intellij.openapi.progress.ProgressIndicator indicator) throws ExportException {
        Path pomPath = modulePath.resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            throw new ExportException("当前工程不是 Maven 模块，无法执行编译: " + modulePath);
        }
        if (indicator != null) {
            indicator.setText("正在 Maven compile: " + modulePath.getFileName());
            indicator.setIndeterminate(true);
        }
        ProcessBuilder processBuilder = createMavenProcessBuilder(pomPath, "compile");
        processBuilder.directory(modulePath.toFile());
        processBuilder.redirectErrorStream(true);
        applyMavenProcessEncoding(processBuilder);
        String output = executeProcess(processBuilder);
        int exitCode = resolveProcessExitCode(output);
        if (exitCode != 0) {
            throw new ExportException("模块 " + modulePath.getFileName() + " Maven 编译失败\n" + summarizeProcessOutput(output));
        }
    }

    /**
     * 统一为 Maven 子进程补齐文件编码，保证编译日志输出和当前读取编码一致。
     *
     * @param processBuilder Maven 进程构建器
     */
    private void applyMavenProcessEncoding(ProcessBuilder processBuilder) {
        String encodingOption = "-Dfile.encoding=" + resolveProcessCharset().name();
        String existingOptions = processBuilder.environment().getOrDefault("MAVEN_OPTS", "");
        if (existingOptions.contains("-Dfile.encoding=")) {
            return;
        }
        // 让 Maven/Javac 输出和当前读取端使用同一编码，避免中文编译错误在 Windows 下乱码。
        processBuilder.environment().put("MAVEN_OPTS", existingOptions.isBlank() ? encodingOption : existingOptions + " " + encodingOption);
    }

    /**
     * 返回当前进程用于读取外部命令输出的编码。
     *
     * @return 进程输出编码
     */
    private Charset resolveProcessCharset() {
        return Charset.defaultCharset();
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

    /**
     * 单模块安装结果，避免并行汇总时重新推断模块名和输出目录。
     *
     * @param moduleName 模块名
     * @param outputDirectory 输出目录
     */
    private record ModuleCompileOutcome(String moduleName, Path outputDirectory) {
    }

    /**
     * Maven 并行执行单元，负责独立安装一个模块并回传结果。
     */
    private class MavenInstallTask implements Callable<ModuleCompileOutcome> {

        private final Path modulePath;
        private final ExportRuntimeReporter reporter;

        /**
         * 初始化单模块安装任务。
         *
         * @param modulePath 模块目录
         * @param reporter 过程上报器
         */
        private MavenInstallTask(Path modulePath, ExportRuntimeReporter reporter) {
            this.modulePath = modulePath;
            this.reporter = reporter;
        }

        /**
         * 执行单模块安装，并把结果回传给主线程统一汇总。
         *
         * @return 模块安装结果
         * @throws ExportException 安装失败
         */
        @Override
        public ModuleCompileOutcome call() throws ExportException {
            compileSingleMavenModule(modulePath, reporter);
            return new ModuleCompileOutcome(modulePath.getFileName().toString(), modulePath.resolve("target").resolve("classes"));
        }
    }
}
