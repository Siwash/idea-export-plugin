package com.seeyon.ideaexport.model;

import java.util.List;

/**
 * 热部署结果，包含成功/失败/跳过的类及耗时。
 */
public record HotDeployResult(
        boolean success,
        String summary,
        List<String> succeededClasses,
        List<String> failedClasses,
        List<String> skippedClasses,
        long elapsedMs,
        String sessionName
) {

    public static HotDeployResult success(List<String> succeededClasses, List<String> skippedClasses, long elapsedMs, String sessionName) {
        int total = succeededClasses.size() + skippedClasses.size();
        return new HotDeployResult(true,
                "成功热部署 " + succeededClasses.size() + " / " + total + " 个类, 耗时 " + elapsedMs + "ms",
                succeededClasses, List.of(), skippedClasses, elapsedMs, sessionName);
    }

    public static HotDeployResult failure(String summary, List<String> succeededClasses, List<String> failedClasses, List<String> skippedClasses, long elapsedMs, String sessionName) {
        return new HotDeployResult(false, summary, succeededClasses, failedClasses, skippedClasses, elapsedMs, sessionName);
    }
}
