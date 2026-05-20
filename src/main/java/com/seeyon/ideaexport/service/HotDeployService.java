package com.seeyon.ideaexport.service;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.HotDeployResult;
import com.seeyon.ideaexport.model.SelectedItem;
import com.seeyon.ideaexport.model.SourceType;
import com.seeyon.ideaexport.service.DebugSessionResolver.DebugSessionInfo;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 热部署服务：Maven 编译 + JDI redefineClasses，实现比 IDEA 更快的热部署。
 *
 * @Author by AI.Coding
 * @Date 2026-05-16
 */
public class HotDeployService {

    private final DebugSessionResolver sessionResolver;
    private final CompileService compileService;

    public HotDeployService() {
        this(new DebugSessionResolver(), new CompileService());
    }

    HotDeployService(DebugSessionResolver sessionResolver, CompileService compileService) {
        this.sessionResolver = Objects.requireNonNull(sessionResolver);
        this.compileService = Objects.requireNonNull(compileService);
    }

    /**
     * 编译选中文件所在模块并热部署到活跃 Debug 会话。
     *
     * @param project       当前项目
     * @param selectedItems 选中的文件列表
     * @return null 表示没有活跃 debug 会话；否则返回热部署结果
     */
    public HotDeployResult compileAndHotDeploy(@NotNull Project project, @NotNull List<SelectedItem> selectedItems) {
        return compileAndHotDeploy(project, selectedItems, null);
    }

    /**
     * 编译选中文件所在模块并热部署到活跃 Debug 会话，同时向后台任务上报阶段进度。
     *
     * @param project       当前项目
     * @param selectedItems 选中的文件列表
     * @param indicator     进度指示器，允许为空
     * @return null 表示没有活跃 debug 会话；否则返回热部署结果
     */
    public HotDeployResult compileAndHotDeploy(@NotNull Project project, @NotNull List<SelectedItem> selectedItems, ProgressIndicator indicator) {
        updateProgress(indicator, "正在解析 Debug 会话...", 0.05D, false);
        List<DebugSessionInfo> sessions = sessionResolver.resolve(project);
        if (sessions.isEmpty()) {
            return null;
        }
        DebugSessionInfo targetSession = sessions.get(0);
        long startTime = System.currentTimeMillis();

        List<SelectedItem> javaItems = selectedItems.stream()
                .filter(item -> item.sourceType() == SourceType.JAVA_SOURCE)
                .toList();
        if (javaItems.isEmpty()) {
            return HotDeployResult.success(List.of(), List.of(), System.currentTimeMillis() - startTime, targetSession.sessionName());
        }

        Map<String, List<SelectedItem>> moduleGroups = groupByModule(javaItems);
        Map<String, Path> moduleOutputDirs;
        try {
            moduleOutputDirs = compileModules(moduleGroups, indicator);
        } catch (ExportException e) {
            return HotDeployResult.failure(e.getMessage(), List.of(), List.of(), List.of(), System.currentTimeMillis() - startTime, targetSession.sessionName());
        }
        if (moduleOutputDirs.isEmpty()) {
            return HotDeployResult.failure("未找到可热部署的 Maven 模块", List.of(), List.of(), List.of(), System.currentTimeMillis() - startTime, targetSession.sessionName());
        }

        updateProgress(indicator, "正在收集 class 文件...", 0.65D, false);
        List<String> classResolveFailures = new ArrayList<>();
        List<ClassToRedefine> classesToRedefine = collectClassesToRedefine(moduleGroups, moduleOutputDirs, classResolveFailures);
        updateProgress(indicator, "正在执行 JVM 热部署...", 0.85D, false);
        HotDeployResult redefineResult = redefineClassesOnManagerThread(targetSession, classesToRedefine, classResolveFailures, startTime);
        if (redefineResult != null) {
            return redefineResult;
        }
        return HotDeployResult.success(List.of(), List.of(), System.currentTimeMillis() - startTime, targetSession.sessionName());
    }

    /**
     * 按模块执行 Maven compile，返回各模块 classes 目录。
     *
     * @param moduleGroups  模块分组选中文件
     * @param targetSession 目标 Debug 会话
     * @param startTime     起始时间
     * @return 模块输出目录映射
     */
    private Map<String, Path> compileModules(Map<String, List<SelectedItem>> moduleGroups, ProgressIndicator indicator) throws ExportException {
        Map<String, Path> moduleOutputDirs = new LinkedHashMap<>();
        int moduleIndex = 0;
        for (String moduleName : moduleGroups.keySet()) {
            moduleIndex++;
            SelectedItem firstItem = moduleGroups.get(moduleName).get(0);
            Path moduleBasePath = firstItem.moduleBasePath();
            double fraction = 0.15D + (0.45D * (moduleIndex - 1) / Math.max(moduleGroups.size(), 1));
            updateProgress(indicator, "正在 Maven compile（" + moduleIndex + "/" + moduleGroups.size() + "）: " + moduleName, fraction, true);
            compileService.compileForHotDeploy(moduleBasePath, indicator);
            moduleOutputDirs.put(moduleName, moduleBasePath.resolve("target").resolve("classes"));
        }
        updateProgress(indicator, "Maven compile 完成", 0.60D, false);
        return moduleOutputDirs;
    }

    /**
     * 在 Debugger manager thread 内执行 JDI 类查询和 redefineClasses。
     *
     * @param targetSession       目标 Debug 会话
     * @param classesToRedefine   待热部署类
     * @param classResolveFailures class 文件解析失败列表
     * @param startTime           起始时间
     * @return 热部署结果
     */
    private HotDeployResult redefineClassesOnManagerThread(DebugSessionInfo targetSession,
                                                           List<ClassToRedefine> classesToRedefine,
                                                           List<String> classResolveFailures,
                                                           long startTime) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HotDeployResult> resultHolder = new AtomicReference<>();
        AtomicReference<Throwable> throwableHolder = new AtomicReference<>();
        targetSession.debuggerSession().getProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() {
                try {
                    resultHolder.set(redefineClasses(targetSession, classesToRedefine, classResolveFailures, startTime));
                } catch (Throwable throwable) {
                    throwableHolder.set(throwable);
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HotDeployResult.failure("等待 Debugger manager thread 执行热部署时被中断", List.of(), List.of(), classResolveFailures, System.currentTimeMillis() - startTime, targetSession.sessionName());
        }
        Throwable throwable = throwableHolder.get();
        if (throwable != null) {
            String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
            return HotDeployResult.failure("JDI redefineClasses 失败: " + message, List.of(), List.of("JDI redefineClasses 失败: " + message), classResolveFailures, System.currentTimeMillis() - startTime, targetSession.sessionName());
        }
        return resultHolder.get();
    }

    /**
     * 执行实际 JDI 热替换；调用方必须保证当前线程是 Debugger manager thread。
     *
     * @param targetSession       目标 Debug 会话
     * @param classesToRedefine   待热部署类
     * @param classResolveFailures class 文件解析失败列表
     * @param startTime           起始时间
     * @return 热部署结果
     */
    private HotDeployResult redefineClasses(DebugSessionInfo targetSession,
                                            List<ClassToRedefine> classesToRedefine,
                                            List<String> classResolveFailures,
                                            long startTime) {
        List<String> succeededClasses = new ArrayList<>();
        List<String> failedClasses = new ArrayList<>(classResolveFailures);
        DebugProcessImpl debugProcess = targetSession.debuggerSession().getProcess();
        com.sun.jdi.VirtualMachine virtualMachine = debugProcess.getVirtualMachineProxy().getVirtualMachine();
        Map<ReferenceType, byte[]> redefineMap = new LinkedHashMap<>();
        for (ClassToRedefine classRedefine : classesToRedefine) {
            List<ReferenceType> refTypes = virtualMachine.classesByName(classRedefine.className);
            if (refTypes.isEmpty()) {
                failedClasses.add(classRedefine.className + " (类未在 JVM 中加载)");
                continue;
            }
            ReferenceType referenceType = selectPreparedReferenceType(refTypes);
            redefineMap.put(referenceType, classRedefine.bytes);
        }
        if (!redefineMap.isEmpty()) {
            // IntelliJ 调试进程要求所有 VM 操作都在 manager thread 执行，否则会触发线程断言。
            virtualMachine.redefineClasses(redefineMap);
            succeededClasses.addAll(redefineMap.keySet().stream().map(ReferenceType::name).toList());
        }
        long elapsed = System.currentTimeMillis() - startTime;
        if (!failedClasses.isEmpty()) {
            return HotDeployResult.failure("部分类热部署失败 (" + failedClasses.size() + " 个), 成功 " + succeededClasses.size() + " 个",
                    succeededClasses, failedClasses, List.of(), elapsed, targetSession.sessionName());
        }
        return HotDeployResult.success(succeededClasses, List.of(), elapsed, targetSession.sessionName());
    }

    /**
     * 优先选择 prepared 的 ReferenceType，避免把未准备完成的类型传入 redefineClasses。
     *
     * @param refTypes 同名类引用
     * @return 可用于 redefine 的类引用
     */
    private ReferenceType selectPreparedReferenceType(List<ReferenceType> refTypes) {
        for (ReferenceType refType : refTypes) {
            if (refType.isPrepared()) {
                return refType;
            }
        }
        return refTypes.get(0);
    }

    /**
     * 按模块名分组选中文件。
     *
     * @param items 选中文件
     * @return 模块名到文件列表的映射
     */
    private Map<String, List<SelectedItem>> groupByModule(List<SelectedItem> items) {
        Map<String, List<SelectedItem>> groups = new LinkedHashMap<>();
        for (SelectedItem item : items) {
            groups.computeIfAbsent(item.moduleName(), k -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    /**
     * 收集 Java 源文件对应的 class 字节码，包含内部类和匿名类。
     *
     * @param moduleGroups        模块分组选中文件
     * @param moduleOutputDirs    模块输出目录映射
     * @param classResolveFailures class 文件解析失败列表
     * @return 待热部署类列表
     */
    private List<ClassToRedefine> collectClassesToRedefine(Map<String, List<SelectedItem>> moduleGroups,
                                                           Map<String, Path> moduleOutputDirs,
                                                           List<String> classResolveFailures) {
        List<ClassToRedefine> classesToRedefine = new ArrayList<>();
        for (Map.Entry<String, List<SelectedItem>> entry : moduleGroups.entrySet()) {
            Path classesDir = moduleOutputDirs.get(entry.getKey());
            if (classesDir == null) {
                continue;
            }
            for (SelectedItem item : entry.getValue()) {
                try {
                    List<Path> classFiles = resolveClassFiles(item, classesDir);
                    for (Path classFile : classFiles) {
                        byte[] bytes = Files.readAllBytes(classFile);
                        String className = classFileToClassName(classFile, classesDir);
                        classesToRedefine.add(new ClassToRedefine(className, bytes));
                    }
                } catch (IOException e) {
                    classResolveFailures.add(item.sourcePath() + " (" + e.getMessage() + ")");
                }
            }
        }
        return classesToRedefine;
    }

    /**
     * 解析 Java 源文件对应的编译产物，包含内部类和匿名类。
     *
     * @param item       选中的 Java 源文件
     * @param classesDir Maven classes 输出目录
     * @return class 文件路径列表
     * @throws IOException class 文件无法定位时抛出
     */
    private List<Path> resolveClassFiles(SelectedItem item, Path classesDir) throws IOException {
        String sourcePath = item.sourcePath().toString().replace('\\', '/');
        int javaIndex = sourcePath.indexOf("/src/main/java/");
        if (javaIndex < 0) {
            throw new IOException("非标准 Maven 源文件路径: " + sourcePath);
        }
        String relativePrefix = sourcePath.substring(javaIndex + "/src/main/java/".length());
        int lastDot = relativePrefix.lastIndexOf('.');
        String className = lastDot >= 0 ? relativePrefix.substring(0, lastDot) : relativePrefix;
        Path baseClassFile = classesDir.resolve(className + ".class");
        List<Path> result = new ArrayList<>();
        if (Files.exists(baseClassFile)) {
            result.add(baseClassFile);
        } else {
            throw new IOException("编译产物不存在: " + baseClassFile);
        }
        String simpleName = className.contains("/") ? className.substring(className.lastIndexOf('/') + 1) : className;
        Path parentDir = baseClassFile.getParent();
        String innerPrefix = simpleName + "$";
        try (Stream<Path> stream = Files.list(parentDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(innerPrefix) && name.endsWith(".class");
            }).forEach(result::add);
        }
        return result;
    }

    /**
     * 把 class 文件路径转换为 JVM 类名。
     *
     * @param classFile class 文件路径
     * @param classesDir classes 根目录
     * @return JVM 类名
     */
    private String classFileToClassName(Path classFile, Path classesDir) {
        String relative = classesDir.relativize(classFile).toString();
        int lastDot = relative.lastIndexOf('.');
        String withoutExt = lastDot >= 0 ? relative.substring(0, lastDot) : relative;
        return withoutExt.replace('/', '.').replace('\\', '.');
    }

    /**
     * 更新后台任务进度；indicator 为空时保持服务可在测试或旧入口中复用。
     *
     * @param indicator     进度指示器
     * @param text          阶段文案
     * @param fraction      进度比例
     * @param indeterminate 是否使用不确定进度
     */
    private void updateProgress(ProgressIndicator indicator, String text, double fraction, boolean indeterminate) {
        if (indicator == null) {
            return;
        }
        indicator.setText(text);
        indicator.setIndeterminate(indeterminate);
        if (!indeterminate) {
            indicator.setFraction(fraction);
        }
    }

    /**
     * 待热部署类字节码。
     *
     * @param className JVM 类名
     * @param bytes     class 字节码
     */
    private record ClassToRedefine(String className, byte[] bytes) {
    }
}
