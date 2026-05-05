# 墨韵（MoYun）AI小说系统功能修复计划

## 📋 问题确认

经过对项目的代码审查，确认以下三个问题确实存在：

### 问题1：流式输出后无功能按钮

**现状**：
- 流式输出对话框（`NovelDetail.vue` 第247行）仅显示生成内容，无任何操作按钮
- 流式生成完成后直接关闭对话框，用户无法对生成的文本进行保存、编辑、删除等操作
- 影响：用户体验差，生成内容后需手动进入编辑页面再次操作

**相关代码**：
- 前端：`/frontend/src/views/NovelDetail.vue` 第247-252行（流式对话框）
- 后端：`/backend/src/main/java/com/novel/system/controller/StreamingChapterController.java`

### 问题2：人物图谱/伏笔/世界时钟功能未充分利用

**现状**：功能已实现但未自动化

#### 人物图谱
- **后端**：`CharacterGraphServiceImpl` 已实现人物关系图谱数据提取
- **前端**：`NovelDetail.vue` 已有图谱对话框（ECharts力导向图）
- **问题**：AI生成章节后，系统不会自动提取新人物并更新图谱，需用户手动触发

#### 伏笔管理
- **后端**：`ClueController` 完整的伏笔CRUD接口
- **前端**：`Clues.vue` 完整的伏笔管理页面
- **问题**：AI生成章节后，系统不会自动识别和创建伏笔，完全依赖用户手动添加

#### 世界时钟
- **后端**：时间线数据模型和API已存在
- **前端**：`NovelDetail.vue` 已有时间线对话框
- **问题**：AI生成章节后，系统不会自动分析和更新时间线变化，需用户手动修改

**根本原因**：缺乏「后处理链」—— AI生成内容后，没有自动触发提取、分析、更新的流程。

### 问题3：向量化功能未生效

**现状**：功能已实现但未调用
- **后端**：`EmbeddingService`（文本向量化）和 `RedisVectorServiceImpl`（Redis向量存储）已完整实现
- **问题**：在日志中未看到向量化调用，说明这些服务未被业务代码实际使用
- **可能原因**：RAG检索流程未启用，或章节内容未触发向量化存储

---

## 🔧 修复计划

### 阶段一：流式输出UI增强（前端，优先级：高）

**目标**：流式生成完成后，显示操作按钮栏

**方案**：
1. 修改 `NovelDetail.vue` 的流式对话框模板：
   - 添加「保存」「编辑」「删除」「重新生成」按钮
   - 生成中显示「取消」按钮（调用 `/chapter/cancel`）
   - 生成完成后显示完整按钮栏
2. 新增状态管理：`streamingStatus`（generating/completed/error）
3. 对接现有API：保存调用 `chapterApi.saveManualChapter`，删除调用现有删除接口

**涉及文件**：
- `/frontend/src/views/NovelDetail.vue`
- `/frontend/src/api/index.ts`（如需新增API）

### 阶段二：后处理自动化链（后端，优先级：高）

**目标**：AI生成章节后，自动触发人物/伏笔/时间线提取

**方案**：
1. 新增 `ChapterPostProcessService` 服务，在 `StreamingChapterController` 的 `onComplete` 回调中调用
2. **人物自动提取**：
   - 调用LLM分析章节内容，提取新人物信息
   - 检查数据库是否存在（按姓名+小说ID），不存在则调用 `CharacterController.createCharacter`
   - 更新人物关系图谱数据
3. **伏笔自动识别**：
   - 调用LLM分析章节内容，识别可能的伏笔
   - 自动创建伏笔记录（类型、描述、埋设章节）
   - 标记伏笔状态（未回收）
4. **时间线自动更新**：
   - 调用LLM分析章节中的时间描述（清晨、午夜、三天后等）
   - 自动计算并更新时间线数据
   - 记录时间变化日志

**涉及文件**：
- `/backend/src/main/java/com/novel/system/service/chapter/ChapterPostProcessService.java`（新建）
- `/backend/src/main/java/com/novel/system/controller/StreamingChapterController.java`
- `/backend/src/main/java/com/novel/system/ai/agent/`（可能需要新增Agent）

### 阶段三：向量化集成（后端，优先级：中）

**目标**：启用RAG向量检索，增强AI生成质量

**方案**：
1. **章节内容向量化**：
   - 在 `ChapterSaveService.saveGenerationResult` 后，调用 `EmbeddingService.embed`
   - 将章节摘要向量存入 `RedisVectorService`
2. **上下文检索增强**：
   - 在 `ContextBuilderService` 中，使用向量检索获取相关历史内容
   - 提供给LLM作为写作上下文
3. **日志增强**：
   - 在 `EmbeddingService` 和 `RedisVectorServiceImpl` 添加INFO级别日志
   - 记录向量化调用次数、耗时、结果数量

**涉及文件**：
- `/backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java`
- `/backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java`
- `/backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java`
- `/backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java`

### 阶段四：前端展示优化（前端，优先级：中）

**目标**：让自动化结果可视化

**方案**：
1. **人物图谱实时更新**：
   - 监听章节生成完成事件，自动刷新图谱数据
   - 新增人物高亮显示动画
2. **伏笔自动展示**：
   - 在章节详情页显示本章创建的伏笔列表
   - 提供快速编辑/确认按钮
3. **时间线可视化**：
   - 增强时间线对话框，显示章节时间变化轨迹
   - 添加时间流图表

**涉及文件**：
- `/frontend/src/views/NovelDetail.vue`
- `/frontend/src/views/Clues.vue`
- `/frontend/src/views/Characters.vue`

---

## 📊 实施建议

### 开发顺序
1. 阶段一 → 阶段二 → 阶段三 → 阶段四

### 测试策略
- 每个阶段完成后，使用相同测试用例验证（生成章节 → 检查按钮/人物/伏笔/向量）
- 建议编写自动化测试脚本

### 回滚方案
- 所有新功能默认关闭，通过功能开关控制
- 配置文件：`application.yml` 新增 `feature.auto-extraction.enabled=false`

### 性能考虑
- 后处理链应异步执行，避免阻塞流式输出响应
- 使用 `@Async` 注解或消息队列实现异步处理

---

## ⚠️ 风险提示

1. **LLM调用成本**：自动提取会增加额外LLM调用，需监控token消耗
2. **数据准确性**：LLM提取的人物/伏笔可能存在误判，建议添加用户确认环节
3. **Redis依赖**：向量化功能依赖Redis Stack，需确认生产环境部署
4. **并发安全**：多人同时编辑时，需考虑数据一致性

---

## 📁 相关文件清单

### 后端关键文件
```
/backend/src/main/java/com/novel/system/controller/StreamingChapterController.java
/backend/src/main/java/com/novel/system/service/chapter/ChapterSaveService.java
/backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java
/backend/src/main/java/com/novel/system/ai/rag/EmbeddingService.java
/backend/src/main/java/com/novel/system/ai/rag/RedisVectorServiceImpl.java
/backend/src/main/java/com/novel/system/ai/agent/CharacterGraphServiceImpl.java
/backend/src/main/java/com/novel/system/controller/CharacterController.java
/backend/src/main/java/com/novel/system/controller/ClueController.java
```

### 前端关键文件
```
/frontend/src/views/NovelDetail.vue
/frontend/src/views/Clues.vue
/frontend/src/views/Characters.vue
/frontend/src/components/novel/CharacterPanel.vue
/frontend/src/api/index.ts
```

---

**预计开发周期**：3-5个工作日（按优先级分阶段实施）

**最后更新**：2026年4月20日