package com.seeyon.ideaexport.service;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编译摘要关切测试，验证 IDEA 编译失败时不会只显示错误数。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class CompileServiceConcernTest {

    private final CompileService compileService = new CompileService();

    /**
     * 验证当上下文里存在真实错误消息时，摘要优先展示错误详情而不是状态占位符。
     */
    @Test
    void shouldPreferCompilerMessagesWhenAvailable() {
        CompileContext compileContext = new StubCompileContext(
                new CompilerMessage[]{new StubCompilerMessage("demo/Test.java", "cannot find symbol")},
                "{COMPILE_SERVER_BUILD_STATUS=ERRORS}"
        );

        String summary = compileService.summarizeIdeaMessages(compileContext, 3);

        // 真实错误消息存在时，必须优先展示文件位置和错误文本，不能只给状态枚举。
        assertTrue(summary.contains("demo/Test.java - cannot find symbol"));
    }

    /**
     * 验证当上下文文本只有状态占位符时，摘要会退回错误数和 Build 窗口指引。
     */
    @Test
    void shouldIncludeReadableFallbackWhenIdeaContextOnlyContainsStatus() {
        CompileContext compileContext = new StubCompileContext(new CompilerMessage[0], "{COMPILE_SERVER_BUILD_STATUS=ERRORS}");

        String summary = compileService.summarizeIdeaMessages(compileContext, 3);

        // 只有状态占位符时不能原样透出，否则用户看不到任何有效错误信息。
        assertTrue(summary.contains("错误数: 3"));
        assertTrue(summary.contains("IDEA Build 窗口"));
    }

    /**
     * 验证当已有可读上下文文本时，优先返回上下文内容。
     */
    @Test
    void shouldPreferContextTextWhenAvailable() {
        CompileContext compileContext = new StubCompileContext(new CompilerMessage[0], "真实错误摘要");

        String summary = compileService.summarizeIdeaMessages(compileContext, 3);

        // 有更具体的上下文文本时，不应退化到通用提示。
        assertEquals("真实错误摘要", summary);
    }

    /**
     * 编译上下文最小桩实现，只覆盖当前摘要逻辑依赖的方法。
     */
    private static final class StubCompileContext implements CompileContext {

        private final CompilerMessage[] errorMessages;
        private final String text;

        private StubCompileContext(CompilerMessage[] errorMessages, String text) {
            this.errorMessages = errorMessages;
            this.text = text;
        }

        @Override
        public CompilerMessage[] getMessages(CompilerMessageCategory category) {
            return category == CompilerMessageCategory.ERROR ? errorMessages : new CompilerMessage[0];
        }

        @Override
        public int getMessageCount(CompilerMessageCategory category) {
            return getMessages(category).length;
        }

        @Override
        public String toString() {
            return text;
        }

        @Override
        public <T> T getUserData(Key<T> key) {
            return null;
        }

        @Override
        public <T> void putUserData(Key<T> key, T value) {
        }

        @Override
        public ProgressIndicator getProgressIndicator() {
            return null;
        }

        @Override
        public CompileScope getCompileScope() {
            return null;
        }

        @Override
        public CompileScope getProjectCompileScope() {
            return null;
        }

        @Override
        public void requestRebuildNextTime(String message) {
        }

        @Override
        public boolean isRebuildRequested() {
            return false;
        }

        @Override
        public String getRebuildReason() {
            return null;
        }

        @Override
        public com.intellij.openapi.module.Module getModuleByFile(VirtualFile file) {
            return null;
        }

        @Override
        public VirtualFile getModuleOutputDirectory(com.intellij.openapi.module.Module module) {
            return null;
        }

        @Override
        public VirtualFile getModuleOutputDirectoryForTests(com.intellij.openapi.module.Module module) {
            return null;
        }

        @Override
        public boolean isMake() {
            return false;
        }

        @Override
        public boolean isAutomake() {
            return false;
        }

        @Override
        public boolean isRebuild() {
            return false;
        }

        @Override
        public Project getProject() {
            return null;
        }

        @Override
        public boolean isAnnotationProcessorsEnabled() {
            return false;
        }

        @Override
        public void addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum, Navigatable navigatable, Collection<String> moduleNames) {
        }
    }

    /**
     * 编译消息最小桩实现，专门用于验证错误摘要拼装。
     */
    private record StubCompilerMessage(String filePath, String messageText) implements CompilerMessage {

        @Override
        public CompilerMessageCategory getCategory() {
            return CompilerMessageCategory.ERROR;
        }

        @Override
        public String getMessage() {
            return messageText;
        }

        @Override
        public Navigatable getNavigatable() {
            return null;
        }

        @Override
        public VirtualFile getVirtualFile() {
            return filePath == null ? null : new StubVirtualFile(filePath);
        }

        @Override
        public String getExportTextPrefix() {
            return "";
        }

        @Override
        public String getRenderTextPrefix() {
            return "";
        }
    }

    /**
     * 虚拟文件最小桩实现，只提供摘要拼装需要的展示路径。
     */
    private static final class StubVirtualFile extends VirtualFile {

        private final String presentableUrl;

        private StubVirtualFile(String presentableUrl) {
            this.presentableUrl = presentableUrl;
        }

        @Override
        public String getName() {
            return presentableUrl;
        }

        @Override
        public com.intellij.openapi.vfs.VirtualFileSystem getFileSystem() {
            return null;
        }

        @Override
        public String getPath() {
            return presentableUrl;
        }

        @Override
        public boolean isWritable() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public VirtualFile getParent() {
            return null;
        }

        @Override
        public VirtualFile[] getChildren() {
            return EMPTY_ARRAY;
        }

        @Override
        public byte[] contentsToByteArray() {
            return new byte[0];
        }

        @Override
        public long getTimeStamp() {
            return 0;
        }

        @Override
        public long getLength() {
            return 0;
        }

        @Override
        public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
        }

        @Override
        public java.io.OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getModificationStamp() {
            return 0;
        }

    }
}
