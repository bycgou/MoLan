package com.novel.system.util;

/**
 * JSON提取工具 — 从LLM响应中提取JSON字符串
 */
public final class JsonExtractor {

    private JsonExtractor() {}

    public static String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");
        }
        int start = trimmed.indexOf('{');
        int startArr = trimmed.indexOf('[');
        if (startArr >= 0 && (start < 0 || startArr < start)) {
            start = startArr;
        }
        if (start > 0) trimmed = trimmed.substring(start);
        int endObj = trimmed.lastIndexOf('}');
        int endArr = trimmed.lastIndexOf(']');
        int end = Math.max(endObj, endArr);
        if (end > 0) trimmed = trimmed.substring(0, end + 1);
        return trimmed;
    }
}
