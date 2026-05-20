package com.seeyon.ideaexport.ui;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结果页日志格式测试，验证控制台超链接过滤器要支持的稳定日志格式。
 *
 * @Author by AI.Coding
 * @Date 2026-04-12
 */
class ExportResultDialogLogFormatTest {

    private static final Pattern WINDOWS_OR_UNIX_LOCATION_PATTERN = Pattern.compile("(?:\\[[^\\]]+\\]\\s+)?((?:[A-Za-z]:[/\\\\]|/).+?\\.java):\\[(\\d+),(\\d+)\\]");
    private static final Pattern IDEA_SUMMARY_PATTERN = Pattern.compile("((?:[A-Za-z]:[/\\\\]|/).+?\\.java)\\s+-\\s+.+");

    /**
     * 验证 Maven/并行日志中的 java:[line,column] 格式可被识别。
     */
    @Test
    void shouldMatchMavenCompilerErrorWithModulePrefix() {
        String line = "[module-a] /D:/code/demo/Test.java:[30,20] 需要 ')'";

        Optional<ParsedLocation> location = parseLocation(line);

        // Maven 并行日志加了模块前缀后，仍必须能命中真正的文件位置。
        assertTrue(location.isPresent());
        assertEquals("/D:/code/demo/Test.java", location.get().path());
        assertEquals(30, location.get().line());
        assertEquals(20, location.get().column());
    }

    /**
     * 验证 IDEA 摘要里的 path - message 格式至少能做到文件级定位。
     */
    @Test
    void shouldMatchIdeaSummaryPathOnlyFormat() {
        String line = "/D:/code/demo/Test.java - cannot find symbol";

        Optional<ParsedLocation> location = parseLocation(line);

        // IDEA 摘要当前拿不到稳定行列号时，也至少要把文件本身变成可点击链接。
        assertTrue(location.isPresent());
        assertEquals("/D:/code/demo/Test.java", location.get().path());
        assertEquals(1, location.get().line());
        assertEquals(1, location.get().column());
    }

    /**
     * 仅模拟结果页过滤器的文本解析规则，避免 UI 测试依赖真实控制台环境。
     *
     * @param line 日志行
     * @return 解析结果
     */
    private Optional<ParsedLocation> parseLocation(String line) {
        Matcher locationMatcher = WINDOWS_OR_UNIX_LOCATION_PATTERN.matcher(line);
        if (locationMatcher.find()) {
            return Optional.of(new ParsedLocation(
                    locationMatcher.group(1),
                    Integer.parseInt(locationMatcher.group(2)),
                    Integer.parseInt(locationMatcher.group(3))
            ));
        }
        Matcher ideaSummaryMatcher = IDEA_SUMMARY_PATTERN.matcher(line);
        if (ideaSummaryMatcher.find()) {
            return Optional.of(new ParsedLocation(ideaSummaryMatcher.group(1), 1, 1));
        }
        return Optional.empty();
    }

    /**
     * 解析出的可定位位置。
     *
     * @param path 文件路径
     * @param line 行号
     * @param column 列号
     */
    private record ParsedLocation(String path, int line, int column) {
    }
}
