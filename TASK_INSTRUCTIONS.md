# 墨韵文风卡片系统 - 实施任务说明书

> **开始日期**: 2026-04-21
> **预计工期**: 11天
> **当前状态**: 执行中

---

## 任务清单

### Phase 1: Anti-AI Prompt 优化 [优先级: 高] [工期: 1-2天]

- [x] 1.1 修改 WritingAgent.java - 增强 SystemMessage
- [x] 1.2 在 ContextBuilderService.java 新增 buildStyleReference() 方法
- [x] 1.3 编译验证

### Phase 2: 数据库建表 [优先级: 高] [工期: 1天]

- [x] 2.1 创建 style_card.sql
- [x] 2.2 创建 style_sample.sql
- [x] 2.3 创建 user_style_library.sql
- [x] 2.4 创建 novel_style_binding.sql

### Phase 3: 后端实体和Mapper [优先级: 高] [工期: 1天]

- [x] 3.1 StyleCard.java 实体类
- [x] 3.2 StyleSample.java 实体类
- [x] 3.3 UserStyleLibrary.java 实体类
- [x] 3.4 NovelStyleBinding.java 实体类
- [x] 3.5 StyleCardMapper.java + XML
- [x] 3.6 StyleSampleMapper.java + XML
- [x] 3.7 UserStyleLibraryMapper.java + XML
- [x] 3.8 NovelStyleBindingMapper.java + XML

### Phase 4: 风格分析引擎 [优先级: 高] [工期: 2天]

- [x] 4.1 StyleMetrics.java - 量化指标DTO
- [x] 4.2 StyleCardAnalysis.java - 分析结果DTO
- [x] 4.3 StyleCardService.java - 核心业务逻辑
- [x] 4.4 StyleTextAnalyzer.java - 程序化文本分析

### Phase 5: 风格注入写作引擎 [优先级: 高] [工期: 1-2天]

- [x] 5.1 修改 ChapterGenerationChain.java - 新增 styleCardId 参数
- [x] 5.2 修改 WritingAgent.java - 支持动态SystemMessage
- [x] 5.3 修改 ContextBuilderService.java - 注入风格内容

### Phase 6: 质量检查 [优先级: 中] [工期: 1天]

- [x] 6.1 StyleCheckResult.java - 检查结果DTO
- [x] 6.2 StyleCheckerService.java - 质量检查服务

### Phase 7: REST API [优先级: 中] [工期: 1天]

- [x] 7.1 StyleCardController.java - 卡片CRUD API
- [x] 7.2 修改 NovelController.java - 绑定风格卡片 (通过bind接口)

### Phase 8: 前端 [优先级: 中] [工期: 2-3天]

- [x] 8.1 类型定义 - style.ts
- [x] 8.2 API接口 - styleApi.ts
- [x] 8.3 StyleCardLibrary.vue - 卡片库页面
- [x] 8.4 StyleSelector.vue - 风格选择器组件
- [x] 8.5 路由配置更新

### Phase 9: 联调测试 [优先级: 低] [工期: 1天]

- [ ] 9.1 编译验证
- [ ] 9.2 功能测试
- [ ] 9.3 效果对比验证

---

## 技术要点

### LangChain4j 动态 SystemMessage 方案

```java
// 方案1: 使用 SystemMessage 注解的变量替换
public interface WritingAgent {
    @SystemMessage("${systemPrompt}")
    String generateChapter(@UserMessage String userPrompt);
}

// 方案2: 使用 ChatMessage 创建自定义消息
// 在 ChapterGenerationChain 中手动构建 messages
```

### 大文本采样策略

1. 按章节分割文本（正则匹配 "第X章"）
2. 采样：前3章 + 中间3章 + 后3章
3. 每章取500字
4. 程序化分析全文（句长、段落、对话比例）
5. LLM分析仅使用采样文本

### Token 消耗控制

- 分析阶段：约1-2万token（采样后）
- 写作注入：约2000-3000token
- 质量检查：快速检查零成本，深度检查约5000token

---

## 文件清单

### 后端新增文件 (12个)

1. `backend/src/main/java/com/novel/system/entity/mysql/StyleCard.java`
2. `backend/src/main/java/com/novel/system/entity/mysql/StyleSample.java`
3. `backend/src/main/java/com/novel/system/entity/mysql/UserStyleLibrary.java`
4. `backend/src/main/java/com/novel/system/entity/mysql/NovelStyleBinding.java`
5. `backend/src/main/java/com/novel/system/mapper/StyleCardMapper.java`
6. `backend/src/main/java/com/novel/system/mapper/StyleSampleMapper.java`
7. `backend/src/main/java/com/novel/system/mapper/UserStyleLibraryMapper.java`
8. `backend/src/main/java/com/novel/system/mapper/NovelStyleBindingMapper.java`
9. `backend/src/main/java/com/novel/system/service/style/StyleCardService.java`
10. `backend/src/main/java/com/novel/system/service/style/StyleTextAnalyzer.java`
11. `backend/src/main/java/com/novel/system/service/style/StyleCheckerService.java`
12. `backend/src/main/java/com/novel/system/controller/StyleCardController.java`

### 后端修改文件 (4个)

1. `backend/src/main/java/com/novel/system/ai/agent/WritingAgent.java`
2. `backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java`
3. `backend/src/main/java/com/novel/system/ai/chain/ChapterGenerationChain.java`
4. `backend/src/main/java/com/novel/system/controller/StreamingChapterController.java`

### 数据库脚本 (4个)

1. `backend/src/main/resources/sql/style_card.sql`
2. `backend/src/main/resources/sql/style_sample.sql`
3. `backend/src/main/resources/sql/user_style_library.sql`
4. `backend/src/main/resources/sql/novel_style_binding.sql`

### 前端新增文件 (7个)

1. `frontend/src/types/style.ts`
2. `frontend/src/api/style.ts`
3. `frontend/src/views/style/StyleCardLibrary.vue`
4. `frontend/src/views/style/CreateStyleCard.vue`
5. `frontend/src/views/style/StyleCardPreview.vue`
6. `frontend/src/components/StyleSelector.vue`

---

## 验证标准

### Phase 1 验证
- [ ] 编译通过
- [ ] 生成章节时AI套话减少80%以上

### Phase 2-4 验证
- [ ] 数据库表创建成功
- [ ] 上传小说文本可创建StyleCard
- [ ] 分析结果包含完整结构化规则

### Phase 5-6 验证
- [ ] 绑定StyleCard后生成的章节风格明显不同
- [ ] 质量检查能正确识别偏离

### Phase 7-8 验证
- [ ] API正常响应
- [ ] 前端可正常创建、选择、使用StyleCard

---

**创建时间**: 2026-04-21
**最后更新**: 2026-04-21
