package com.seeyon.ideaexport.service;

import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析当前项目中活跃的 Java Debug 会话，供热部署服务在 Debugger manager thread 内访问目标 JVM。
 *
 * @Author by AI.Coding
 * @Date 2026-05-16
 */
public class DebugSessionResolver {

    /**
     * 活跃 Java Debug 会话信息。
     *
     * @param sessionName     会话显示名
     * @param debuggerSession IntelliJ Java Debugger 会话
     * @param xDebugSession   IntelliJ XDebugSession
     */
    public record DebugSessionInfo(
            String sessionName,
            DebuggerSession debuggerSession,
            XDebugSession xDebugSession
    ) {
    }

    /**
     * 获取当前项目所有活跃的 Java Debug 会话。
     *
     * @param project 当前项目
     * @return 活跃 Java Debug 会话列表
     */
    public @NotNull List<DebugSessionInfo> resolve(@NotNull Project project) {
        List<DebugSessionInfo> result = new ArrayList<>();
        XDebugSession[] sessions = XDebuggerManager.getInstance(project).getDebugSessions();
        for (XDebugSession session : sessions) {
            if (session.isStopped()) {
                continue;
            }
            DebugSessionInfo info = extractSessionInfo(session);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 从 XDebugSession 中提取 Java DebuggerSession。
     *
     * @param session XDebug 会话
     * @return Java Debug 会话，非 Java 调试会话返回 null
     */
    private DebugSessionInfo extractSessionInfo(XDebugSession session) {
        XDebugProcess debugProcess = session.getDebugProcess();
        if (!(debugProcess instanceof JavaDebugProcess javaProcess)) {
            return null;
        }
        DebuggerSession debuggerSession = javaProcess.getDebuggerSession();
        if (debuggerSession == null) {
            return null;
        }
        // 这里只保存 DebuggerSession，不能在 EDT 上触碰 getVirtualMachineProxy，否则会触发 manager thread 断言。
        return new DebugSessionInfo(session.getSessionName(), debuggerSession, session);
    }
}
