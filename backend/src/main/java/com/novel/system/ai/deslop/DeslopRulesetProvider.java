package com.novel.system.ai.deslop;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 反 AI 味规则表的提供者：懒加载 + 缓存。
 *
 * <p>规则表是随包的静态资源，但校验失败必须让应用起不来（报错而不是带病运行），
 * 所以第一次访问时抛出的异常会直接冒泡到启动阶段。
 */
@Component
public class DeslopRulesetProvider {

    /** 规则表在 classpath 下的位置。 */
    public static final String DEFAULT_RESOURCE = "deslop/deslop-rules.yaml";

    private final String resourcePath;
    private volatile DeslopRuleset cached;

    public DeslopRulesetProvider() {
        this(DEFAULT_RESOURCE);
    }

    public DeslopRulesetProvider(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public DeslopRuleset get() {
        DeslopRuleset local = cached;
        if (local == null) {
            synchronized (this) {
                local = cached;
                if (local == null) {
                    local = load();
                    cached = local;
                }
            }
        }
        return local;
    }

    private DeslopRuleset load() {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("找不到反 AI 味规则表: " + resourcePath);
        }
        try (InputStream in = resource.getInputStream()) {
            return DeslopRulesetLoader.parse(in);
        } catch (Exception e) {
            throw new IllegalStateException("加载反 AI 味规则表失败: " + resourcePath + " —— " + e.getMessage(), e);
        }
    }
}
