package com.novel.system.util;

/**
 * JSON提取工具 — 从LLM响应中提取JSON字符串
 */
public final class JsonExtractor {

    private JsonExtractor() {}

    public static String extractJson(String response) {
        return extractJson(response, "{}");
    }

    public static String extractJson(String response, String defaultJson) {
        if (response == null) return defaultJson;
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");
        }
        // 删除中文装饰性引号，避免LLM输出中的非标准引号破坏JSON解析
        // 注意：不能替换为ASCII双引号，否则会截断JSON字符串
        trimmed = trimmed.replace('“', ' ')  // 左双引号 "
                         .replace('”', ' ')  // 右双引号 "
                         .replace('‘', ' ')  // 左单引号 '
                         .replace('’', ' ')  // 右单引号 '
                         .replace('＂', ' ')  // 全角双引号 ＂
                         .replace('＇', ' '); // 全角单引号 ＇
        int start = trimmed.indexOf('[');
        int startObj = trimmed.indexOf('{');
        if (start < 0 || (startObj >= 0 && startObj < start)) {
            start = startObj;
        }
        if (start > 0) trimmed = trimmed.substring(start);
        int endObj = trimmed.lastIndexOf('}');
        int endArr = trimmed.lastIndexOf(']');
        int end = Math.max(endObj, endArr);
        if (end > 0) trimmed = trimmed.substring(0, end + 1);
        return trimmed;
    }
}
