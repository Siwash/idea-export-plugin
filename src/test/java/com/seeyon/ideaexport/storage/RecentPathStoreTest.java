package com.seeyon.ideaexport.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史路径存储测试，验证 LRU、去重与损坏配置恢复行为。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
class RecentPathStoreTest {

    @TempDir
    Path tempDir;

    /**
     * 验证首次加载时会自动创建空配置。
     *
     * @throws IOException 文件操作失败
     */
    @Test
    void shouldCreateEmptyConfigWhenMissing() throws IOException {
        RecentPathStore store = new RecentPathStore(tempDir.resolve("config"));

        List<String> paths = store.load();

        // 首次初始化必须创建空配置，保证后续 record 可以直接落盘。
        assertTrue(paths.isEmpty());
        assertTrue(Files.exists(store.getConfigFile()));
    }

    /**
     * 验证重复路径会提升到首位，并按 LRU 保留最近使用记录。
     *
     * @throws IOException 文件操作失败
     */
    @Test
    void shouldDeduplicateAndMoveLatestPathToFront() throws IOException {
        RecentPathStore store = new RecentPathStore(tempDir.resolve("config"));

        store.record("/tmp/a");
        store.record("/tmp/b");
        store.record("/tmp/a");

        // 再次使用同一路径时必须前移，不能出现重复记录。
        assertEquals(List.of("/tmp/a", "/tmp/b"), store.load());
    }

    /**
     * 验证历史路径超出上限时只保留最近 30 条。
     *
     * @throws IOException 文件操作失败
     */
    @Test
    void shouldKeepOnlyThirtyRecentPaths() throws IOException {
        RecentPathStore store = new RecentPathStore(tempDir.resolve("config"));

        for (int index = 0; index < 35; index++) {
            store.record("/tmp/path-" + index);
        }

        List<String> paths = store.load();
        assertEquals(30, paths.size());
        assertEquals("/tmp/path-34", paths.get(0));
        assertEquals("/tmp/path-5", paths.get(29));
    }

    /**
     * 验证配置损坏时会自动重建空配置，并保留备份文件。
     *
     * @throws IOException 文件操作失败
     */
    @Test
    void shouldBackupBrokenConfigAndResetHistory() throws IOException {
        Path configDirectory = tempDir.resolve("config");
        Files.createDirectories(configDirectory);
        Path configFile = configDirectory.resolve("recent-paths.json");
        Files.writeString(configFile, "{broken-json", StandardCharsets.UTF_8);
        RecentPathStore store = new RecentPathStore(configDirectory);

        List<String> paths = store.load();

        // 损坏配置必须可恢复，否则插件首次异常后将一直不可用。
        assertTrue(paths.isEmpty());
        assertTrue(Files.exists(configDirectory.resolve("recent-paths.json.bak")));
    }
}
