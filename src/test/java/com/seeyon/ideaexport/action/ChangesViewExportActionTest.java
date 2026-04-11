package com.seeyon.ideaexport.action;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.testFramework.LightVirtualFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Git 变更入口可见性测试，验证提交区和历史区的上下文兼容性。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class ChangesViewExportActionTest {

    private final ChangesViewExportAction action = new ChangesViewExportAction();

    /**
     * 验证存在严格选中 changes 时，菜单会显示。
     */
    @Test
    void shouldSupportSelectedChangesContext() {
        DataContext dataContext = buildDataContext(new Change[0], null, null);

        // 只要 Git 变更区给了 SELECTED_CHANGES，入口就必须显示。
        assertEquals(true, action.hasSupportedContext(dataContext));
    }

    /**
     * 验证提交区只提供文件数组时，菜单也会显示。
     */
    @Test
    void shouldSupportCommitToolWindowVirtualFileArray() {
        DataContext dataContext = buildDataContext(null, new LightVirtualFile[]{new LightVirtualFile("Test.java")}, null);

        // 提交区常只给通用文件数组，不给 SELECTED_CHANGES，因此这里也必须判定为可用。
        assertEquals(true, action.hasSupportedContext(dataContext));
    }

    /**
     * 验证完全没有文件上下文时，菜单不会显示。
     */
    @Test
    void shouldRejectEmptyContext() {
        DataContext dataContext = buildDataContext(null, null, null);

        // 没有任何文件选择时继续显示菜单只会把用户带到无效操作。
        assertEquals(false, action.hasSupportedContext(dataContext));
    }

    /**
     * 构造最小数据上下文，专门用于可见性判断测试。
     *
     * @param changes Git 变更数组
     * @param fileArray 通用文件数组
     * @param singleFile 单文件
     * @return 最小数据上下文
     */
    private DataContext buildDataContext(Change[] changes, LightVirtualFile[] fileArray, LightVirtualFile singleFile) {
        return dataId -> {
            if (VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
                return changes;
            }
            if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
                return fileArray;
            }
            if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
                return singleFile;
            }
            return null;
        };
    }
}
