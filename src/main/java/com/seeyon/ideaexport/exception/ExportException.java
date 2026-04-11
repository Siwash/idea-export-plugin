package com.seeyon.ideaexport.exception;

/**
 * 导出流程统一异常，负责向上层传递可读失败原因。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class ExportException extends Exception {

    /**
     * 使用异常消息创建导出异常。
     *
     * @param message 异常描述
     */
    public ExportException(String message) {
        super(message);
    }

    /**
     * 使用异常消息和根因创建导出异常。
     *
     * @param message 异常描述
     * @param cause 根因异常
     */
    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
