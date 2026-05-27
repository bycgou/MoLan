# 题材内容补全与功能增强设计

## 目标

1. 为 5 个新题材创建完整的 Markdown 写作指南（5 × 5 个文件 + index.md）
2. 新增单文件 API、热加载、智能截断三项功能增强

## 前置条件

- `GenreService` / `GenreServiceImpl` 已就位（commit `03c59da`）
- 6 个已有题材的 `index.md` 已就位（commit `5804060`）
- `GenreController` 已迁移至 `/genre/` 路径（commit `03c59da`）

---

## 一、内容生成

### 1.1 范围

| code | 名称 | category | 指南文件 |
|------|------|----------|----------|
| dushi | 都市 | 男频 | `职场描写.md`、`都市异能设定.md`、`感情线设计.md`、`商业博弈.md`、`都市人物塑造.md` |
| xianxia | 仙侠 | 男频 | `修仙体系.md`、`仙界设定.md`、`战斗描写.md`、`法宝丹药.md`、`宗门势力.md` |
| kehuan | 科幻 | 男频 | `科技设定.md`、`世界观构建.md`、`科幻概念.md`、`太空歌剧.md`、`人工智能.md` |
| youxi | 游戏 | 男频 | `游戏系统设计.md`、`电竞描写.md`、`虚拟世界.md`、`玩家心理.md`、`副本设计.md` |
| moshi | 末世 | 男频 | `末世生存.md`、`丧尸设定.md`、`人性冲突.md`、`基地建设.md`、`异能体系.md` |

共 30 个文件（5 个 index.md + 25 个指南文件）。

### 1.2 生成方式

使用 LLM 生成初稿。每个指南文件由一个 prompt 模板驱动，输入：
- 题材名称、描述、套路、避坑指南（来自 `initTemplatesForMissing()` 硬编码数据）
- 指南主题名称
- 参考现有题材指南的结构和风格

输出：1500-3000 字的结构化 Markdown（标题 + 列表 + 示例）。

### 1.3 index.md 格式

与现有题材一致，使用 YAML front matter：

```markdown
---
code: dushi
name: 都市
category: 男频
description: 现实背景、职场逆袭、商业争霸
tropes:
  - 职场逆袭：从小职员到CEO
  - ...
pitfalls:
  - 避免过于脱离现实
  - ...
characterArchetypes:
  主角: 低调务实、能力出众
  ...
rhythmConfig:
  questRatio: 0.55
  fireRatio: 0.3
  constellationRatio: 0.15
  questMaxConsecutive: 4
  fireMaxGap: 8
  constellationMaxGap: 18
---

# 都市题材总览

都市小说以现实世界为背景...
```

### 1.4 质量标准

- 每个指南文件 1500-3000 字
- 结构化：使用 `##` 标题分节、`-` 列表、代码块示例
- 与现有题材（如 `xuanhuan/cultivation-levels.md`）风格一致
- 包含具体写作建议，不泛泛而谈

### 1.5 清理

所有 5 个新题材的 `index.md` 就位后，删除 `GenreServiceImpl.initTemplatesForMissing()` 方法，因为所有题材数据都来自 Markdown。

---

## 二、功能增强

### 2a. 单文件 API

**端点：** `GET /genre/{code}/guides/{filename}`

**行为：**
- 读取 `classpath:genres/{code}/{filename}` 文件内容
- 返回纯文本（Markdown）
- 文件不存在返回 404

**GenreService 新增方法：**
```java
String getGenreGuideFile(String code, String filename);
```

**实现：**
- `mapGenreToFolder(code)` 解析目录名
- 校验 filename 不含 `..` 路径穿越
- 读取文件返回内容

### 2b. 热加载

**两套机制：**

**WatchService（开发环境）：**
- 新建 `GenreWatchService.java`，`@Profile("dev")` + `@Component`
- 使用 `java.nio.file.WatchService` 监听 `classpath:genres/` 目录
- 检测到 `CREATE` / `MODIFY` / `DELETE` 事件时，延迟 500ms 防抖后调用 `genreService.reload()`
- `@PreDestroy` 关闭 WatchService

**手动刷新 API：**
- `POST /genre/reload`
- 调用 `genreService.reload()`
- 返回刷新后的题材数量

**GenreService 新增方法：**
```java
void reload();
```

**实现：** 清空 `templates` map，重新调用 `loadFromMarkdown()`。

### 2c. 智能截断

**当前行为：** `loadMarkdownGuide()` 硬切字符，可能在句子中间截断。

**新行为：** 按 `## ` 标题层级截断。

**算法：**
```
for each line in file:
    if line starts with "## ":
        if accumulatedChars > maxCharsPerFile:
            break  # 在标题处截断
    accumulatedChars += line.length
    append line to result
```

**回退：** 如果单个标题段落（从 `## ` 到下一个 `## `）本身就超过 `maxCharsPerFile`，在段落内硬切（保留原有行为作为兜底）。

---

## 三、文件变更清单

### 新建文件

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/novel/system/service/genre/GenreWatchService.java` | dev profile WatchService |
| `backend/src/main/resources/genres/dushi/index.md` | 都市题材元数据 |
| `backend/src/main/resources/genres/dushi/*.md` (5 个) | 都市写作指南 |
| `backend/src/main/resources/genres/xianxia/index.md` | 仙侠题材元数据 |
| `backend/src/main/resources/genres/xianxia/*.md` (5 个) | 仙侠写作指南 |
| `backend/src/main/resources/genres/kehuan/index.md` | 科幻题材元数据 |
| `backend/src/main/resources/genres/kehuan/*.md` (5 个) | 科幻写作指南 |
| `backend/src/main/resources/genres/youxi/index.md` | 游戏题材元数据 |
| `backend/src/main/resources/genres/youxi/*.md` (5 个) | 游戏写作指南 |
| `backend/src/main/resources/genres/moshi/index.md` | 末世题材元数据 |
| `backend/src/main/resources/genres/moshi/*.md` (5 个) | 末世写作指南 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `GenreService.java` | 新增 `getGenreGuideFile()`、`reload()` |
| `GenreServiceImpl.java` | 实现新方法、智能截断、删除 `initTemplatesForMissing()` |
| `GenreController.java` | 新增 `GET /{code}/guides/{filename}`、`POST /reload` |

---

## 四、风险与缓解

| 风险 | 缓解 |
|------|------|
| LLM 生成内容质量不稳定 | 生成后人工审核，不合格的重新生成 |
| WatchService 在某些 OS 上不触发 | 手动刷新 API 作为兜底 |
| 智能截断在极端情况下仍可能截断不当 | 保留硬切作为回退 |
| 删除 `initTemplatesForMissing()` 后 index.md 解析失败 | `loadFromMarkdown()` 的 catch 块已处理异常，不会阻断启动 |
