package com.seeyon.ideaexport.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.seeyon.ideaexport.model.RecentPathState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 用户目录历史导出路径存储，负责去重、LRU 和空配置自动恢复。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class RecentPathStore {

    private static final int MAX_HISTORY_SIZE = 30;
    private static final String CONFIG_DIR = ".idea-export-seeyon";
    private static final String CONFIG_FILE = "recent-paths.json";
    private static final Type STATE_TYPE = new TypeToken<RecentPathState>() {
    }.getType();

    private final Gson gson;
    private final Path configDirectory;
    private final Path configFile;

    /**
     * 使用默认用户目录初始化历史路径存储。
     */
    public RecentPathStore() {
        this(Paths.get(System.getProperty("user.home"), CONFIG_DIR));
    }

    /**
     * 使用指定目录初始化历史路径存储，便于测试覆盖边界场景。
     *
     * @param configDirectory 配置目录
     */
    public RecentPathStore(Path configDirectory) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory cannot be null");
        this.configFile = configDirectory.resolve(CONFIG_FILE);
    }

    /**
     * 读取历史导出路径；首次使用或配置缺失时自动创建空配置。
     *
     * @return 历史路径列表
     * @throws IOException 读取或修复配置失败
     */
    public List<String> load() throws IOException {
        ensureConfigReady();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            RecentPathState state = gson.fromJson(reader, STATE_TYPE);
            if (Objects.isNull(state) || Objects.isNull(state.paths())) {
                // 配置文件为空对象时按空配置恢复，避免后续逻辑出现空指针。
                return List.of();
            }
            return normalize(state.paths());
        } catch (JsonParseException exception) {
            // 配置损坏时先备份原文件，方便用户排查，再重建空配置继续工作。
            backupBrokenConfig();
            writeState(RecentPathState.empty());
            return List.of();
        }
    }

    /**
     * 记录一次新的导出路径，并按 LRU 规则刷新顺序。
     *
     * @param path 本次使用的导出路径
     * @throws IOException 配置写入失败
     */
    public void record(String path) throws IOException {
        Objects.requireNonNull(path, "path cannot be null");
        String normalizedPath = path.trim();
        if (normalizedPath.isEmpty()) {
            return;
        }

        List<String> existingPaths = new ArrayList<>(load());
        existingPaths.removeIf(existingPath -> Objects.equals(existingPath, normalizedPath));
        existingPaths.add(0, normalizedPath);
        writeState(new RecentPathState(normalize(existingPaths)));
    }

    /**
     * 返回当前配置文件路径，便于 UI 和测试场景展示与断言。
     *
     * @return 配置文件路径
     */
    public Path getConfigFile() {
        return configFile;
    }

    /**
     * 确保配置目录和配置文件存在；首次使用时自动创建空 JSON。
     *
     * @throws IOException 创建目录或文件失败
     */
    private void ensureConfigReady() throws IOException {
        Files.createDirectories(configDirectory);
        if (Files.exists(configFile)) {
            return;
        }
        writeState(RecentPathState.empty());
    }

    /**
     * 将路径列表去重、裁剪并保持最近使用项在前。
     *
     * @param paths 原始路径列表
     * @return 归一化后的历史路径
     */
    private List<String> normalize(List<String> paths) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String path : paths) {
            if (Objects.isNull(path)) {
                continue;
            }
            String trimmed = path.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            deduplicated.add(trimmed);
            if (deduplicated.size() == MAX_HISTORY_SIZE) {
                break;
            }
        }
        return List.copyOf(deduplicated);
    }

    /**
     * 持久化历史路径状态，统一处理目录创建与编码。
     *
     * @param state 历史路径状态
     * @throws IOException 写入失败
     */
    private void writeState(RecentPathState state) throws IOException {
        Files.createDirectories(configDirectory);
        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            gson.toJson(state, STATE_TYPE, writer);
        }
    }

    /**
     * 备份损坏配置文件，避免修复过程覆盖原始问题现场。
     *
     * @throws IOException 备份失败
     */
    private void backupBrokenConfig() throws IOException {
        if (!Files.exists(configFile)) {
            return;
        }
        Path backupFile = configDirectory.resolve("recent-paths.json.bak");
        Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
    }
}
