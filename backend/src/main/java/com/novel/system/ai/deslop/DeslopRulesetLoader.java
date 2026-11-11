package com.novel.system.ai.deslop;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 反 AI 味规则表的加载与校验。
 *
 * <p>纯逻辑，不依赖 Spring —— 校验规则表是唯一能在改规则后立刻验证的东西，
 * 所以它必须能脱离容器单测。
 *
 * <p>校验不通过一律抛异常（报错而不是带病运行）。
 */
public final class DeslopRulesetLoader {

    /** 已实现的检测器白名单。 */
    public static final Set<String> SUPPORTED_DETECTORS = Set.of("sentence_length_run");

    /** 已实现的白名单作用域。 */
    public static final Set<String> SUPPORTED_WHITELIST_SCOPES = Set.of("dialogue");

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    private DeslopRulesetLoader() {
    }

    public static DeslopRuleset parse(InputStream in) {
        return parse(readAll(in));
    }

    public static DeslopRuleset parse(String yamlText) {
        Object loaded;
        try {
            loaded = new Yaml().load(yamlText);
        } catch (RuntimeException e) {
            throw new IllegalStateException("反 AI 味规则表 YAML 语法错误: " + e.getMessage(), e);
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalStateException("反 AI 味规则表顶层必须是映射，实际是 " + describe(loaded));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) loaded;
        return build(root);
    }

    private static DeslopRuleset build(Map<String, Object> root) {
        int version = intValue(root.get("version"), "version");
        if (version <= 0) {
            throw new IllegalStateException("反 AI 味规则表 version 必须是正整数，实际是 " + version);
        }

        List<String> whitelistScopes = stringList(root.get("whitelist_scopes"));
        for (String scope : whitelistScopes) {
            if (!SUPPORTED_WHITELIST_SCOPES.contains(scope)) {
                throw new IllegalStateException("反 AI 味规则表白名单作用域不支持: " + scope
                        + "（已支持: " + SUPPORTED_WHITELIST_SCOPES + "）");
            }
        }

        Object rawRules = root.get("rules");
        if (!(rawRules instanceof List) || ((List<?>) rawRules).isEmpty()) {
            throw new IllegalStateException("反 AI 味规则表 rules 至少要有 1 条规则");
        }

        List<DeslopRule> rules = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Object raw : (List<?>) rawRules) {
            if (!(raw instanceof Map)) {
                throw new IllegalStateException("反 AI 味规则表 rules 元素必须是映射，实际是 " + describe(raw));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> ruleMap = (Map<String, Object>) raw;
            DeslopRule rule = buildRule(ruleMap);
            if (!seenIds.add(rule.getId())) {
                throw new IllegalStateException("反 AI 味规则表存在重复规则 id: " + rule.getId());
            }
            rules.add(rule);
        }
        return new DeslopRuleset(version, rules, whitelistScopes);
    }

    private static DeslopRule buildRule(Map<String, Object> map) {
        String id = stringValue(map.get("id"));
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalStateException("反 AI 味规则 id 必须是 kebab-case，实际是: " + id);
        }

        DeslopSeverity severity = enumValue(map.get("severity"), DeslopSeverity.class, id, "severity");
        DeslopReview review = enumValue(map.get("review"), DeslopReview.class, id, "review");

        String pattern = stringValue(map.get("pattern"));
        DeslopDensity density = buildDensity(map.get("density"), id);
        String detector = stringValue(map.get("detector"));

        int judgementCount = (pattern != null ? 1 : 0) + (density != null ? 1 : 0) + (detector != null ? 1 : 0);
        if (judgementCount != 1) {
            throw new IllegalStateException("反 AI 味规则 " + id
                    + " 的 pattern / density / detector 必须恰好给一个，实际给了 " + judgementCount + " 个");
        }
        if (detector != null && !SUPPORTED_DETECTORS.contains(detector)) {
            throw new IllegalStateException("反 AI 味规则 " + id + " 使用了未实现的检测器: " + detector
                    + "（已实现: " + SUPPORTED_DETECTORS + "）");
        }
        if (pattern != null) {
            assertPatternCompiles(pattern, id);
        }
        if (density != null) {
            assertPatternCompiles(density.getPattern(), id);
        }

        if (severity == DeslopSeverity.BLOCKING && review != DeslopReview.MECHANICAL) {
            throw new IllegalStateException("反 AI 味规则 " + id
                    + " 违反铁律一：blocking 只允许 review: mechanical —— 语义判断永远不能挡稿");
        }
        if (review == DeslopReview.SEMANTIC && severity != DeslopSeverity.ADVISORY) {
            throw new IllegalStateException("反 AI 味规则 " + id
                    + " 违反铁律二：review: semantic 的规则一律 advisory");
        }

        String autofix = stringValue(map.get("autofix"));
        if (autofix != null) {
            if (!"delete".equals(autofix)) {
                throw new IllegalStateException("反 AI 味规则 " + id + " 的 autofix 只支持 delete，实际是 " + autofix);
            }
            if (density == null) {
                throw new IllegalStateException("反 AI 味规则 " + id + " 违反铁律三：autofix 只允许给 density 规则");
            }
            if (review != DeslopReview.MECHANICAL || severity == DeslopSeverity.BLOCKING) {
                throw new IllegalStateException("反 AI 味规则 " + id
                        + " 违反铁律三：autofix 必须 review: mechanical 且非 blocking");
            }
        }

        String message = stringValue(map.get("message"));
        if (message == null) {
            throw new IllegalStateException("反 AI 味规则 " + id + " 缺少 message");
        }
        String suggestion = stringValue(map.get("suggestion"));
        if (suggestion == null) {
            throw new IllegalStateException("反 AI 味规则 " + id + " 缺少 suggestion");
        }

        List<String> languages = stringList(map.get("languages"));
        if (languages.isEmpty()) {
            languages = List.of("zh");
        }

        DeslopScope scope = map.containsKey("scope")
                ? enumValue(map.get("scope"), DeslopScope.class, id, "scope")
                : DeslopScope.FULL;

        return DeslopRule.builder()
                .id(id)
                .severity(severity)
                .review(review)
                .pattern(pattern)
                .scope(scope)
                .density(density)
                .detector(detector)
                .autofix(autofix)
                .message(message)
                .suggestion(suggestion)
                .languages(languages)
                .evidence(stringValue(map.get("evidence")))
                .build();
    }

    private static DeslopDensity buildDensity(Object raw, String ruleId) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map)) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 的 density 必须是映射");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        String pattern = stringValue(map.get("pattern"));
        if (pattern == null) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 的 density 缺少 pattern");
        }
        int per = intValue(map.get("per"), ruleId + ".density.per");
        if (per <= 0) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 的 density.per 必须是正整数，实际是 " + per);
        }
        Object rawLimit = map.get("limit");
        if (!(rawLimit instanceof Number)) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 的 density.limit 必须是数字");
        }
        double limit = ((Number) rawLimit).doubleValue();
        if (limit <= 0) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 的 density.limit 必须是正数，实际是 " + limit);
        }
        return new DeslopDensity(pattern, per, limit);
    }

    private static <E extends Enum<E>> E enumValue(Object raw, Class<E> type, String ruleId, String field) {
        String text = stringValue(raw);
        if (text == null) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 缺少 " + field);
        }
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 的 " + field + " 取值非法: " + text);
        }
    }

    private static String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private static int intValue(Object raw, String field) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalStateException("反 AI 味规则表 " + field + " 必须是整数，实际是 " + describe(raw));
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List)) {
            throw new IllegalStateException("反 AI 味规则表期望列表，实际是 " + describe(raw));
        }
        Set<String> values = new LinkedHashSet<>();
        for (Object item : (List<?>) raw) {
            if (item != null) {
                values.add(String.valueOf(item).trim());
            }
        }
        return new ArrayList<>(values);
    }

    private static String describe(Object raw) {
        return raw == null ? "null" : raw.getClass().getSimpleName();
    }

    static String readAll(InputStream in) {
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取反 AI 味规则表失败: " + e.getMessage(), e);
        }
    }

    static void assertPatternCompiles(String pattern, String ruleId) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException("反 AI 味规则 " + ruleId + " 的正则无法编译: " + e.getMessage(), e);
        }
    }
}
