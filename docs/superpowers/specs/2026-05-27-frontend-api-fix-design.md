# 前端 API 全量适配修复设计

## 目标

修复 Vue 3 前端与 Spring Boot 后端之间的全部 API 适配问题（16 项），包括接口路径修复、DTO 对齐、请求格式修正，以及 9 个后端已有端点的前端视图层接入。

## 范围

| # | 类别 | 问题数 |
|---|------|--------|
| 1 | Genre 模块修复（URL + 类型） | 3 个端点 |
| 2 | Rhythm 模块修复（请求/响应格式） | 2 个端点 |
| 3 | Character 模块修复（DTO 对齐） | 1 个端点 |
| 4 | LLM Config 模块修复（DTO 对齐） | 4 个端点 |
| 5 | Stream URL 修复 | 1 处 |
| 6 | Growth node 响应类型 | 1 个端点 |
| 7 | 新增前端调用 + 视图层接入（9 个端点） | 9 个端点 |

## 方案

**纯前端适配** — 只修改前端代码去匹配后端现有接口，不改动后端。唯一例外：Novel 回收站需要后端 `GET /novel/list` 支持 `?isDeleted=true` 参数。

---

## 第一节：Genre 模块修复

### 问题

前端调用 `/genre-template/list`、`/genre-template/by-genre`、`/genre-template/{id}`，后端已改为 `/genre/list`、`/genre/{genreCode}`、`/genre/{genreCode}/rhythm`。

### 改动

**API 层**（`frontend/src/api/index.ts`）：
- `genreTemplateApi` 重命名为 `genreApi`
- `getTemplateList()` → `GET /genre/list`，返回类型改为 `GenreInfo[]`
- `getTemplatesByGenre(genre)` → `GET /genre/{genreCode}`，返回类型改为 `GenreTemplate`
- 删除 `getTemplate(templateId)`（后端无此端点，前端也未使用）
- 新增 `getGenreRhythm(genreCode)` → `GET /genre/{genreCode}/rhythm`

**类型定义**（`frontend/src/types/index.ts`）：
- 新增 `GenreInfo` 接口：`{ code, name, category, description }`
- `GenreTemplateVO` 对齐后端 `GenreTemplate`：移除 `coreThemes`/`plotPatterns`/`status`，新增 `category`/`writingStyle`/`rhythmConfig`
- 新增 `GenreRhythmConfig` 接口：`{ questRatio, fireRatio, constellationRatio, questMaxConsecutive, fireMaxGap, constellationMaxGap }`

**视图层**（`CreateNovel.vue`）：
- `onGenreChange` 改调 `genreApi.getTemplatesByGenre(genreCode)`
- 返回单个对象（非数组），直接赋值给 `genreTemplate`
- UI 只用 `tropes`、`pitfalls`、`characterArchetypes`，这三个字段在后端 `GenreTemplate` 中都存在，无需改模板

**CreateNovelWizard.vue**：删除未使用的 `genreTemplateApi` 导入

### 后端字段映射

| 前端 GenreTemplateVO | 后端 GenreTemplate | UI 使用 |
|---|---|---|
| code | code | 否 |
| name | name | 否 |
| description | description | 否 |
| tropes | tropes | ✅ 列表展示 |
| pitfalls | pitfalls | ✅ 列表展示 |
| characterArchetypes | characterArchetypes | ✅ 卡片展示 |
| ~~coreThemes~~ | — | 删除，后端无此字段 |
| ~~plotPatterns~~ | — | 删除，后端无此字段 |
| ~~status~~ | — | 删除，后端无此字段 |
| — | category | 新增 |
| — | writingStyle | 新增 |
| — | rhythmConfig | 新增 |

---

## 第二节：Rhythm + Character + Growth 修复

### Rhythm read-pull

- **前端当前**：`POST /novel/{id}/rhythm/read-pull`，body 为 `{ chapterNum, chapterContent }` JSON
- **后端期望**：`?chapterNum=N` query param + raw string body
- **修复**：`analyzeReadPull(novelId, chapterNum, chapterContent)` 改为 `api.post(url, chapterContent, { params: { chapterNum }, headers: { 'Content-Type': 'text/plain' } })`

### Rhythm check

- **前端当前**：返回类型 `boolean`
- **后端返回**：`RhythmViolation` 对象（含 `strandType`、`message`、`severity` 等字段）
- **修复**：返回类型改为 `RhythmViolation`，新增 `RhythmViolation` 接口，调用处判断逻辑从 `if (res.data)` 改为 `if (res.data !== null)`

### Character update

- **前端当前**：发 `UpdateCharacterRequest`（name/role/description/personality/background）
- **后端期望**：`CharacterState` 实体，只用 `currentStatus` 和 `relationshipMap`
- **修复**：请求体改为只发 `{ currentStatus, relationshipMap }`，新增 `CharacterUpdatePayload` 类型

### Growth node

- **前端当前**：`POST /novel/{id}/character/{charId}/growth`，类型 `ApiResponse<GrowthNode>`
- **后端返回**：`ApiResponse<Void>`
- **修复**：返回类型改为 `ApiResponse<null>`，store 层不使用返回值所以无需改逻辑

---

## 第三节：LLM Config DTO 对齐

### 改动

读取后端 `LlmConfig` 实体字段，确认所有字段名和类型，前端类型对齐：

- `CreateLlmConfigRequest` — 确保字段名与后端 `LlmConfig` 一致（`configName`、`provider`、`apiKey`、`apiBaseUrl`、`modelName`、`temperature`、`maxTokens`、`topP`、`isActive`、`usageType`）
- `UpdateLlmConfigRequest` — 同上，所有字段 optional
- `TestConnectionRequest` — 只需 `provider`、`apiKey`、`apiBaseUrl`、`modelName`
- `LlmConfigVO` — 对齐后端返回的 `LlmConfig` 字段，移除前端多余的字段（如后端没有 `authType`、`userId`）
- `testConnection` 返回类型：后端返回 `ApiResponse<String>`（消息），前端期望 `boolean` → 改为 `String`

---

## 第四节：Stream URL 修复

- `getStreamWriteUrl()` 当前返回 `/api/chapter/stream-write`
- axios baseURL 已是 `/api`，配合使用会变成 `/api/api/chapter/stream-write` → 404
- **修复**：返回值改为 `/chapter/stream-write`（去掉 `/api` 前缀）

---

## 第五节：Novel 回收站（恢复 + 永久删除）

### 后端依赖

后端有 `restore` 和 `hard delete` 端点，但没有"列出已删除小说"的端点。需要后端 `GET /novel/list` 支持 `?isDeleted=true` 查询参数。

### 前端改动

**API 层新增：**
```ts
getDeletedNovelList: () => api.get('/novel/list', { params: { isDeleted: true } })
restoreNovel: (id: string) => api.post(`/novel/${id}/restore`)
hardDeleteNovel: (id: string) => api.delete(`/novel/${id}/hard`)
```

**视图层（Dashboard）：**
- 小说列表增加"回收站"按钮/tab
- 点击后调用 `GET /novel/list?isDeleted=true` 获取已删除小说
- 每个已删除小说显示"恢复"和"永久删除"按钮
- 永久删除带 `ElMessageBox.confirm` 二次确认
- 操作成功后刷新列表

---

## 第六节：Character 关系编辑 + 图谱

### 关系编辑

- 后端签名：`PUT /novel/{id}/character/{charId}/relationship?targetCharacterId=X&value=N`
- 前端在角色详情页增加"关系管理"区域
- 显示当前角色的关系列表（从图谱接口获取）
- 提供"添加/修改关系"表单：选择目标角色 + 关系值（数值滑块或输入框）
- 调用时用 query params 传参

**API 层新增：**
```ts
updateRelationship: (novelId, charId, targetCharacterId, value) =>
  api.put(`/novel/${novelId}/character/${charId}/relationship`, null, { params: { targetCharacterId, value } })
```

### 关系图谱

- 调用 `GET /novel/{id}/graph` 获取 `CharacterGraph` 数据
- 使用 vis-network（轻量、零配置、支持 Canvas 渲染）作为图表库
- 节点 = 角色，边 = 关系，边标签 = 关系类型
- 嵌入 CharacterView 页面底部，使用 `el-collapse` 折叠面板

**API 层新增：**
```ts
getRelationshipGraph: (novelId: string) => api.get(`/novel/${novelId}/graph`)
```

---

## 第七节：Clue 批量回收 + 章节伏笔查询

### 批量回收

- 后端签名：`PUT /novel/{id}/clue/batch-recycle?recycleChapter=N`，body 为 `List<String>` clueIds
- 伏笔列表增加 checkbox 列
- 顶部出现"批量回收"按钮（选中 ≥1 个时显示）
- 点击后弹出确认对话框，需要输入回收章节号
- 成功后刷新列表

**API 层新增：**
```ts
batchRecycleClues: (novelId, clueIds, recycleChapter) =>
  api.put(`/novel/${novelId}/clue/batch-recycle`, clueIds, { params: { recycleChapter } })
```

### 章节伏笔查询

- `GET /novel/{id}/clue/bury/{chapterNum}` — 本章埋设的伏笔
- `GET /novel/{id}/clue/recycle/{chapterNum}` — 本章回收的伏笔
- 章节详情页增加两个折叠区域
- 以列表形式展示伏笔名称、描述、状态

**API 层新增：**
```ts
getCluesByBuryChapter: (novelId, chapterNum) => api.get(`/novel/${novelId}/clue/bury/${chapterNum}`)
getCluesByRecycleChapter: (novelId, chapterNum) => api.get(`/novel/${novelId}/clue/recycle/${chapterNum}`)
```

---

## 第八节：体裁指南浏览

### 位置

CreateNovel.vue 中体裁选择后，在"题材指导"区域下方增加"写作指南"折叠面板。

### 交互

- 选择体裁后调用 `GET /genre/{code}/guides` 获取文件名列表
- 展示为可点击的文件列表
- 点击文件调用 `GET /genre/{code}/guides/{filename}` 获取内容
- 内容在 `el-dialog` 对话框中显示

**API 层新增：**
```ts
getGuideList: (code: string) => api.get(`/genre/${code}/guides`)
getGuideFile: (code: string, filename: string) => api.get(`/genre/${code}/guides/${filename}`)
```

---

## 文件改动清单

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `frontend/src/api/index.ts` | 修改 | 修复 genre/rhythm/character/llmConfig API，新增 novel recycle/clue/graph API |
| `frontend/src/types/index.ts` | 修改 | 对齐 GenreTemplateVO，新增 GenreInfo/RhythmViolation/GenreRhythmConfig 等类型 |
| `frontend/src/views/CreateNovel.vue` | 修改 | genre API 调用修复，新增体裁指南浏览 |
| `frontend/src/views/CreateNovelWizard.vue` | 修改 | 删除未使用的 genreTemplateApi 导入 |
| `frontend/src/views/Dashboard.vue` | 修改 | 新增回收站 tab |
| `frontend/src/views/CharacterView.vue` | 修改 | 新增关系编辑 + 图谱 |
| `frontend/src/views/CluePanel.vue` | 修改 | 新增批量回收 + checkbox |
| `frontend/src/views/ChapterView.vue` | 修改 | 新增本章伏笔查询 |
| `frontend/src/stores/novel.ts` | 修改 | 新增 restore/hardDelete 方法 |
| `frontend/src/stores/character.ts` | 修改 | 新增 updateRelationship、getGraph 方法 |
| `frontend/src/stores/clue.ts` | 修改 | 新增 batchRecycle、getByChapter 方法 |
| `backend/.../NovelController.java` | 修改 | `GET /novel/list` 支持 `?isDeleted=true` 参数 |
| `backend/.../NovelService.java` | 修改 | 新增按 isDeleted 过滤的 list 方法 |
| `backend/.../NovelServiceImpl.java` | 修改 | 实现 isDeleted 过滤逻辑 |

## 依赖新增

| 包 | 用途 | 安装 |
|---|---|---|
| `vis-network` | 角色关系图谱可视化 | `npm install vis-network vis-data` |

## 不在范围

- 后端 `EnhancedRhythmController` 合并到 `RhythmController`（属于后端重构）
- 后端死代码清理（已单独分析，用户决定暂不修改）
- `rhythm-enhanced/*` 3 个端点的前端接入（与 `rhythm/*` 功能重复）
- `POST /novel/{id}/graph/relationship` 端点接入（功能与 `PUT character/{charId}/relationship` 重叠）
- `GET /novel/{id}/graph/character/{charId}` 端点接入（图谱接口已包含此信息）
