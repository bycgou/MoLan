package com.novel.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis 键前缀提供者。
 * 部署环境的 Redis 开启了 ACL，仅授权 {@code xpb:} 前缀下的键，
 * 因此业务键统一由这里拼接前缀，避免出现「写了但没权限」的静默失败。
 */
@Component
public class RedisKeyProvider {

    private final String prefix;

    public RedisKeyProvider(@Value("${novel.redis.key-prefix:xpb:}") String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public String checkpoint() {
        return prefix + "novel:checkpoint:";
    }

    public String context() {
        return prefix + "novel:context:";
    }

    public String taskStatus() {
        return prefix + "novel:task:status:";
    }

    public String wizardBatch() {
        return prefix + "novel:wizard:batch:";
    }

    public String cancel() {
        return prefix + "novel:cancel:";
    }

    public String prefix() {
        return prefix;
    }
}
