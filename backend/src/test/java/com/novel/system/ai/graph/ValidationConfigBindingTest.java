package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验随包 application.yml 中的 novel.validation 配置能被正确绑定。
 * 该组用例用于防止「yml 键名与代码查询键不一致」导致配置项静默失效。
 */
class ValidationConfigBindingTest {

    private ValidationConfig config;

    @BeforeEach
    void setUp() throws IOException {
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .get(0);
        Binder binder = new Binder(ConfigurationPropertySources.from(source));
        config = binder.bind("novel.validation", ValidationConfig.class).orElseGet(ValidationConfig::new);
    }

    @Test
    void gradedAuditIsEnabledByDefault() {
        assertTrue(config.isUseGradedAudit(),
                "七维加权评分路由应默认开启，否则系统实际走 legacy 路由");
        assertEquals(60.0, config.getPassThreshold(), 1e-6);
    }

    @Test
    void allSevenDimensionsAreBoundWithDistinctKeys() {
        Map<String, ValidationConfig.DimensionConfig> dims = config.getDimensions();

        assertEquals(7, dims.size(), "应绑定 7 个维度，实际：" + dims.keySet());
        for (String key : new String[]{"ooc", "logic", "setting", "clue", "rhythm", "quality", "clueLanding"}) {
            assertTrue(dims.containsKey(key), "缺少维度键：" + key + "，实际键集：" + dims.keySet());
        }
    }

    @Test
    void clueLandingDimensionIsNotDeadConfig() {
        ValidationConfig.DimensionConfig dc = config.getDimensions().get("clueLanding");
        assertTrue(dc != null, "clueLanding 维度未绑定，yml 键名与代码查询键不一致");
        assertEquals(0.10, dc.getWeight(), 1e-6);
        assertEquals(ValidationConfig.Severity.WARNING, dc.getSeverity());
    }

    @Test
    void weightsSumToOne() {
        double sum = config.getDimensions().values().stream()
                .mapToDouble(ValidationConfig.DimensionConfig::getWeight)
                .sum();
        assertEquals(1.0, sum, 1e-6, "各维度权重之和应为 1");
    }

    @Test
    void fatalDimensionsAreMarked() {
        Map<String, ValidationConfig.DimensionConfig> dims = config.getDimensions();
        assertEquals(ValidationConfig.Severity.FATAL, dims.get("ooc").getSeverity());
        assertEquals(ValidationConfig.Severity.FATAL, dims.get("logic").getSeverity());
        assertEquals(ValidationConfig.Severity.FATAL, dims.get("setting").getSeverity());
        assertFalse(dims.get("quality").getSeverity() == ValidationConfig.Severity.FATAL);
    }
}
