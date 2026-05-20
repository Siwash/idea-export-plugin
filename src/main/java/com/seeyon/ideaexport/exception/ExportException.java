package com.seeyon.ideaexport.exception;

/**
 * 导出流程统一异常，负责向上层传递可读失败原因。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class ExportException extends Exception {

    private final java.nio.file.Path sourcePath;
    private final int line;
    private final int column;

    /**
     * 使用异常消息创建导出异常。
     *
     * @param message 异常描述
     */
    public ExportException(String message) {
        super(message);
        this.sourcePath = null;
        this.line = -1;
        this.column = -1;
    }

    /**
     * 使用异常消息和根因创建导出异常。
     *
     * @param message 异常描述
     * @param cause 根因异常
     */
    public ExportException(String message, Throwable cause) {
        super(message, cause);
        this.sourcePath = null;
        this.line = -1;
        this.column = -1;
    }

    /**
     * 使用异常消息和来源文件创建导出异常，供结果窗口执行失败定位。
     *
     * @param message 异常描述
     * @param sourcePath 失败来源文件
     */
    public ExportException(String message, java.nio.file.Path sourcePath) {
        this(message, sourcePath, -1, -1);
    }

    /**
     * 使用异常消息、来源文件和定位信息创建导出异常。
     *
     * @param message 异常描述
     * @param sourcePath 失败来源文件
     * @param line 失败行号
     * @param column 失败列号
     */
    public ExportException(String message, java.nio.file.Path sourcePath, int line, int column) {
        super(message);
        this.sourcePath = sourcePath;
        this.line = line;
        this.column = column;
    }

    /**
     * 返回失败来源文件；拿不到时返回 null。
     *
     * @return 失败来源文件
     */
    public java.nio.file.Path getSourcePath() {
        return sourcePath;
    }

    /**
     * 返回失败行号；拿不到时返回 -1。
     *
     * @return 行号
     */
    public int getLine() {
        return line;
    }

    /**
     * 返回失败列号；拿不到时返回 -1。
     *
     * @return 列号
     */
    public int getColumn() {
        return column;
    }
}
