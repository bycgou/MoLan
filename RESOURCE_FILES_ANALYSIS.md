# 墨韵项目资源文件（Markdown）使用情况分析

## 📁 项目资源目录结构

后端项目的 `src/main/resources/` 目录下包含三个主要的Markdown文件集合：

```
resources/
├── agents/          # AI代理配置文件（8个文件）
├── genres/          # 题材写作指南（29个文件）
└── references/      # 参考文档（10个文件）
```

---

## ✅ 使用情况详细分析

### 1. `genres/` 目录（题材指南）✅ **正在使用**

**状态**：已完全集成到AI生成流程

**使用位置**：
- `PromptTemplateServiceImpl.buildGenreGuide()` 方法（第105-136行）
- `GenreTemplateServiceImpl.loadMarkdownTemplates()` 方法（第27-41行）

**工作机制**：
```java
// 1. 根据小说题材类型加载对应目录
Resource[] resources = resourceResolver.getResources(
    "classpath:genres/" + genreKey + "/*.md");

// 2. 读取并拼接所有文件内容
StringBuilder sb = new StringBuilder();
for (Resource r : resources) {
    String content = StreamUtils.copyToString(r.getInputStream(), 
        StandardCharsets.UTF_8);
    sb.append("【").append(name).append("】\n").append(content).append("\n\n");
}

// 3. 注入到AI提示词模板
effectiveVariables.put("genre_guide", buildGenreGuide(effectiveVariables.get("novel_genre")));
```

**实际效果**：
- 当生成玄幻小说章节时，自动加载 `genres/xuanhuan/` 下的4个文件
- 为AI提供具体的题材写作指导（角色原型、情节模板、避坑指南等）
- 在大纲生成和正文生成提示词中通过 `{genre_guide}` 占位符注入

**支持的题材**：
- `xuanhuan/` - 玄幻（4个文件）
- `dog-blood-romance/` - 狗血言情（7个文件）
- `period-drama/` - 古装剧（5个文件）
- `realistic/` - 现实题材（5个文件）
- `rules-mystery/` - 规则悬疑（7个文件）
- `zhihu-short/` - 知乎短篇（7个文件）

---

### 2. `agents/` 目录（AI代理配置）❌ **未使用**

**状态**：存在但未被Java代码调用

**文件清单**：
1. `consistency-checker.md` - 设定一致性检查器
2. `context-agent.md` - 上下文搜集Agent
3. `continuity-checker.md` - 连续性检查器
4. `data-agent.md` - 数据整理Agent
5. `high-point-checker.md` - 爽点检查器
6. `ooc-checker.md` - 人设一致性检查器
7. `pacing-checker.md` - 节奏检查器
8. `reader-pull-checker.md` - 追读力检查器

**文件格式**：
```yaml
---
name: consistency-checker
description: 设定一致性检查，输出结构化报告供润色步骤参考
tools: Read, Grep, Bash
model: inherit
---
```

**设计用途**：
- 为 **Claude Code** 或其他AI工具插件设计的配置文件
- 定义了各种AI代理的行为规范、输入输出格式
- 包含工具配置、执行流程、输出格式等规范

---

### 3. `references/` 目录（参考文档）❌ **未使用**

**状态**：存在但未被Java代码调用

**文件清单**：
1. `checker-output-schema.md` - 检查器输出格式规范
2. `claude-code-call-matrix.md` - Claude Code调用矩阵
3. `context-contract-v2.md` - 上下文契约
4. `entity-management-spec.md` - 实体管理规范
5. `genre-profiles.md` - 题材档案
6. `preferences-schema.md` - 偏好设置规范
7. `project-memory-schema.md` - 项目记忆规范
8. `reading-power-taxonomy.md` - 追读力分类学
9. `shared/cool-points-guide.md` - 爽点指南
10. `shared/core-constraints.md` - 核心约束
11. `shared/strand-weave-pattern.md` - Strand节奏模式

**设计用途**：
- 被 `agents/` 文件引用，作为AI代理的参考资料
- 提供详细的规范、分类、格式定义
- 是Claude Code插件的辅助文档

---

## 🔧 使用建议

### 方案一：集成到现有提示词系统（推荐）

**适用场景**：希望利用这些文件增强AI生成质量，但不引入外部工具

**实施步骤**：

1. **扩展 `PromptTemplateServiceImpl`**：
```java
/**
 * 加载agent配置文件
 */
public String loadAgentConfig(String agentType) {
    try {
        Resource resource = resourceResolver.getResource(
            "classpath:agents/" + agentType + ".md");
        if (resource.exists()) {
            return StreamUtils.copyToString(resource.getInputStream(), 
                StandardCharsets.UTF_8);
        }
    } catch (Exception e) {
        log.warn("加载agent配置失败: {}", e.getMessage());
    }
    return "";
}

/**
 * 提取配置文件中的关键部分
 */
private String extractAgentInstructions(String agentConfig) {
    // 解析YAML frontmatter
    // 提取执行流程、输出格式等关键部分
    // 返回可用于提示词的内容
}
```

2. **在提示词模板中引用**：
```java
// 在渲染提示词时注入agent配置
effectiveVariables.put("consistency_checker_instructions", 
    extractAgentInstructions(loadAgentConfig("consistency-checker")));
```

3. **在验证链中使用**：
```java
// 使用consistency-checker的规范进行设定一致性检查
// 使用ooc-checker的规范进行人设一致性检查
```

**优点**：
- 无需引入外部工具
- 可以逐步集成，按需使用
- 提升AI生成质量的可控性

**缺点**：
- 需要开发解析逻辑
- 部分配置可能不适用于当前架构

---

### 方案二：为Claude Code插件准备

**适用场景**：计划未来集成Claude Code或其他AI编程助手

**实施步骤**：

1. **保留目录结构**：这些文件遵循Claude Code插件规范
2. **配置插件路径**：在Claude Code配置中指定 `CLAUDE_PLUGIN_ROOT`：
   ```json
   {
     "claudePlugin": {
       "rootPath": "src/main/resources"
     }
   }
   ```
3. **直接使用**：Claude Code会自动读取这些配置文件

**优点**：
- 无需修改现有代码
- 完整支持Claude Code功能
- 可以利用现有的agent配置

**缺点**：
- 依赖外部工具
- 需要团队学习使用Claude Code

---

### 方案三：清理未使用文件

**适用场景**：确定不需要这些功能，希望简化项目结构

**实施步骤**：

1. **备份重要文件**：
   ```bash
   # 创建备份目录
   mkdir -p /path/to/backup/resources/
   
   # 备份
   cp -r src/main/resources/agents /path/to/backup/
   cp -r src/main/resources/references /path/to/backup/
   ```

2. **删除未使用文件**：
   ```bash
   # 删除agents和references目录
   rm -rf src/main/resources/agents
   rm -rf src/main/resources/references
   
   # 保留genres目录（正在使用）
   ```

3. **更新文档**：在项目README中说明删除原因

**优点**：
- 简化项目结构
- 减少仓库体积
- 避免维护无用文件

**缺点**：
- 丢失潜在功能
- 未来可能需要重新创建

---

## 📊 使用情况总结

| 目录 | 文件数量 | 状态 | 用途 | 建议 |
|------|---------|------|------|------|
| `genres/` | 29个 | ✅ 使用中 | 题材写作指南，注入AI提示词 | **保留并扩展** |
| `agents/` | 8个 | ❌ 未使用 | Claude Code代理配置 | **按需选择方案** |
| `references/` | 10个+ | ❌ 未使用 | 参考文档，被agents引用 | **按需选择方案** |

---

## 🎯 立即行动建议

### 1. 检查genres目录覆盖度
```bash
# 查看支持的题材类型
ls -la src/main/resources/genres/

# 检查每个题材的文件完整性
for genre in src/main/resources/genres/*/; do
  echo "=== $(basename $genre) ==="
  ls -la "$genre"
done
```

### 2. 评估AI生成质量
- 测试不同题材的章节生成
- 检查genre_guide是否正确注入
- 收集用户对生成质量的反馈

### 3. 制定长期计划
- **如果AI生成质量满意**：保持现状，考虑清理agents/references
- **如果需要提升质量**：选择方案一或方案二进行集成
- **如果计划引入Claude Code**：保留所有文件，按方案二配置

---

## 📝 相关文件清单

### 正在使用的文件（genres目录）
```
src/main/resources/genres/xuanhuan/
├── cultivation-levels.md    # 修仙境界体系
├── power-systems.md         # 力量系统设计
├── xuanhuan-cool-points.md  # 玄幻爽点指南
└── xuanhuan-plot-patterns.md # 玄幻情节模板

src/main/resources/genres/dog-blood-romance/
├── character-archetypes.md  # 角色原型
├── emotional-tension.md     # 情绪张力
├── plot-templates.md        # 情节模板
├── romance-pacing.md        # 恋爱节奏
├── romance-tropes.md        # 恋爱套路
├── sweet-moments.md         # 甜蜜时刻
└── torture-points.md        # 虐点设计

... 其他题材目录类似
```

### 未使用的文件（agents/references目录）
```
src/main/resources/agents/    # 8个AI代理配置文件
src/main/resources/references/ # 10+个参考文档
```

---

**分析完成时间**：2026年4月20日  
**分析结论**：genres目录已有效集成，agents/references目录可根据项目发展方向选择保留、集成或清理