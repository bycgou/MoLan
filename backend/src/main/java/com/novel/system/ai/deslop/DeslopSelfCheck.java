package com.novel.system.ai.deslop;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 规则表与检测器的离线自检入口（不依赖 Spring，不依赖数据库）。
 *
 * <p>用途：在本机跑不起 MoLan（MongoDB/Redis/Milvus 端口被封）的环境里，
 * 单独验证「规则表能加载」与「检测器按预期报」。
 *
 * <pre>
 * sh mvnw -q compile
 * sh mvnw exec:java -Dexec.mainClass=com.novel.system.ai.deslop.DeslopSelfCheck \
 *     -Dexec.classpathScope=runtime -Dexec.args="样例正文.txt"
 * </pre>
 */
public final class DeslopSelfCheck {

    private static final String DEFAULT_SAMPLE = """
            他走进那间屋子，把灯打开，霉味散开一些。
            「你回来了。」她靠在门框上，没有动。
            窗外的雨小了些，屋檐还在滴水。
            他终于明白，这一切的背后，是一个更大的阴谋。
            """;

    private DeslopSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        DeslopRuleset ruleset = loadRuleset();
        System.out.printf("规则表加载成功：version=%d, rules=%d, whitelist=%s%n",
                ruleset.getVersion(), ruleset.getRules().size(), ruleset.getWhitelistScopes());

        String body = readBody(args);
        List<DeslopFinding> findings = MechanicalDeslopDetector.scan(body, ruleset, 3);

        System.out.printf("%n正文 %d 字，命中 %d 条：%n", body.length(), findings.size());
        for (DeslopFinding finding : findings) {
            System.out.printf("  [%s] %s%n    命中：%s%n    改法：%s%n",
                    finding.getSeverity(), finding.getRuleId(),
                    finding.getQuote(), finding.getSuggestion());
        }

        long blocking = findings.stream()
                .filter(f -> f.getSeverity() == DeslopSeverity.BLOCKING)
                .count();
        System.out.printf("%nblocking=%d（只有 blocking 会挡稿，其余只计数）%n", blocking);
    }

    private static DeslopRuleset loadRuleset() {
        try (InputStream in = DeslopSelfCheck.class.getClassLoader()
                .getResourceAsStream(DeslopRulesetProvider.DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("找不到规则表资源: " + DeslopRulesetProvider.DEFAULT_RESOURCE);
            }
            return DeslopRulesetLoader.parse(in);
        } catch (Exception e) {
            throw new IllegalStateException("加载规则表失败: " + e.getMessage(), e);
        }
    }

    private static String readBody(String[] args) throws Exception {
        if (args.length == 0) {
            return DEFAULT_SAMPLE;
        }
        return new String(Files.readAllBytes(Path.of(args[0])), StandardCharsets.UTF_8);
    }
}
