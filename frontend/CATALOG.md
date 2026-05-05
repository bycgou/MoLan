# MoYun Frontend - Exhaustive UI Catalog

## 1. PROJECT OVERVIEW

- **Framework**: Vue 3.4 + TypeScript + Vite 5
- **UI Library**: Element Plus 2.4 (full import, all icons registered)
- **State Management**: Pinia 2.1
- **Routing**: Vue Router 4.2 (HTML5 history mode)
- **HTTP Client**: Axios (180s timeout)
- **Rich Text Editor**: WangEditor 5 (`@wangeditor/editor-for-vue`)
- **Charts**: ECharts 5.4
- **Dev Server**: Vite on port 3000, proxy `/api` -> `http://localhost:8080`

---

## 2. ALL ROUTES (from router/index.ts)

| Path | Name | Component | Auth | Notes |
|------|------|-----------|------|-------|
| `/login` | Login | `views/Login.vue` | Guest only | Login/Register tabs |
| `/` | Home | `views/Home.vue` | Required | Welcome + stats |
| `/dashboard` | Dashboard | `views/Dashboard.vue` | Required | Charts + novel selector |
| `/novels` | Novels | `views/Novels.vue` | Required | Novel card grid |
| `/create` | Create | `views/CreateNovel.vue` | Required | Create novel form |
| `/novel/:novelId` | NovelDetail | `views/NovelDetail.vue` | Required | Main novel workspace |
| `/novel/:novelId/edit` | NovelEdit | `views/NovelEdit.vue` | Required | Edit novel metadata |
| `/novel/:novelId/chapter/:chapterNum` | ChapterDetail | `views/ChapterDetail.vue` | Required | Read chapter |
| `/novel/:novelId/chapter/:chapterNum/edit` | ChapterEdit | `views/ChapterEdit.vue` | Required | Rich text chapter editor |
| `/novel/:novelId/characters` | Characters | `views/Characters.vue` | Required | Character management |
| `/novel/:novelId/clues` | Clues | `views/Clues.vue` | Required | Foreshadowing/clue management |
| `/llm-config` | LlmConfig | `views/LlmConfig.vue` | Required | LLM model configuration |
| `/style-cards` | StyleCards | `views/style/StyleCardLibrary.vue` | Required | Writing style card library |
| `/:pathMatch(.*)*` | NotFound | `views/NotFound.vue` | None | 404 page |

---

## 3. LAYOUT / SHELL STRUCTURE

**File**: `src/components/Layout.vue`

The root `App.vue` renders `<Layout />`.

### Layout Architecture:
```
+------------------+-----------------------------------+
|   SIDEBAR (200px)|   MAIN CONTENT (flex: 1)          |
|                  |                                   |
|  Logo: "AI小说系统" |  <router-view />                |
|                  |                                   |
|  el-menu (dark)  |                                   |
|  - 首页 /        |                                   |
|  - 仪表盘        |                                   |
|  - 我的小说      |                                   |
|  - 创建小说      |                                   |
|  - 文风卡片库    |                                   |
|  - 模型配置      |                                   |
|                  |                                   |
|  Footer:         |                                   |
|  [username]      |                                   |
|  [退出登录]      |                                   |
+------------------+-----------------------------------+
```

- **Guest routes** (like `/login`): Render WITHOUT sidebar, just `<router-view>` directly
- **Auth routes**: Wrapped in sidebar + main content layout
- Sidebar: Dark background (#001529), Element Plus `el-menu` with `router` mode
- Active menu item tracks `route.path`

---

## 4. AUTH FLOW

### Login Page (`views/Login.vue`)
- **Two tabs**: Login / Register (el-tabs)
- **Login form**: username + password
- **Register form**: username + nickname + email + password + confirmPassword
- Form validation rules on all fields
- On success, redirects to `/novels`

### Token Management (`stores/authStore.ts`)
- Token stored in `localStorage.getItem('token')`
- On login: stores `res.data.token` to localStorage
- On logout: removes from localStorage
- `fetchUser()` calls `GET /api/auth/me` to validate token

### Route Guards (`router/index.ts`)
- `beforeEach` guard checks:
  1. No token + not guest route -> redirect to `/login`
  2. Token + guest route -> redirect to `/novels`
  3. Token + not guest -> validates via `authStore.fetchUser()`; if fails, clears token -> `/login`

### Axios Interceptors (`api/index.ts`)
- **Request**: Auto-attaches `Authorization: Bearer <token>` header
- **Response**: 
  - 401 -> clears token, redirects to `/login`
  - 403/404/500 -> ElMessage error display

---

## 5. STATE MANAGEMENT (Pinia Stores)

### `authStore` (`stores/authStore.ts`)
- **State**: `user`, `token`, `loading`
- **Computed**: `isLoggedIn`, `username`, `role`
- **Actions**: `login()`, `register()`, `fetchUser()`, `logout()`

### `novelStore` (`stores/novelStore.ts`)
- **State**: `novels[]`, `currentNovel`, `chapters[]`, `characters[]`, `clues[]`, `loadingNovels`, `loadingNovel`
- **Computed**: `novelCount`, `currentNovelId`
- **Actions**: `fetchNovels()`, `fetchNovel()`, `deleteNovel()`, `fetchChapters()`, `fetchCharacters()`, `createCharacter()`, `updateCharacter()`, `addGrowthNode()`, `fetchClues()`, `createClue()`, `recycleClue()`, `$reset()`

### `configStore` (`stores/configStore.ts`)
- **State**: `configs[]`, `loading`
- **Computed**: `activeConfigs` (filtered by isActive===1)
- **Actions**: `fetchConfigs()`, `createConfig()`, `updateConfig()`, `deleteConfig()`, `testConnection()`, `$reset()`

---

## 6. API SERVICE LAYER

### Base Config (`api/index.ts`)
- Axios instance: `baseURL: '/api'`, timeout: 180s
- All API methods return `res.data` (the `ApiResponse<T>` envelope)

### Auth API (`authApi`)
| Method | HTTP | Endpoint | Returns |
|--------|------|----------|---------|
| `login(data)` | POST | `/api/auth/login` | `UserVO` |
| `register(data)` | POST | `/api/auth/register` | `UserVO` |
| `getCurrentUser()` | GET | `/api/auth/me` | `UserVO` |

### Novel API (`novelApi`)
| Method | HTTP | Endpoint | Returns |
|--------|------|----------|---------|
| `createNovel(data)` | POST | `/api/novel` | `NovelVO` |
| `getNovelList()` | GET | `/api/novel/list` | `NovelVO[]` |
| `getNovel(id)` | GET | `/api/novel/:id` | `NovelVO` |
| `updateNovel(id, data)` | PUT | `/api/novel/:id` | `NovelVO` |
| `deleteNovel(id)` | DELETE | `/api/novel/:id` | null |
| `exportNovel(id)` | GET | `/api/novel/:id/export` | blob |
| `autoGenerateOutline(data)` | POST | `/api/novel/auto-generate-outline` | outline+worldSetting |
| `generateOutline(id, params)` | POST | `/api/novel/:id/outline` | outline+ideas |
| `getNovelGraph(id)` | GET | `/api/novel/:id/novel-graph` | `NovelGraphVO` |
| `getTimeline(id)` | GET | `/api/novel/:id/timeline` | `TimelineVO` |
| `updateTimeline(id, data)` | PUT | `/api/novel/:id/timeline` | `TimelineVO` |

### Chapter API (`chapterApi`)
| Method | HTTP | Endpoint | Returns |
|--------|------|----------|---------|
| `generateChapter(id, data)` | POST | `/api/novel/:id/chapter` | `ChapterVO` |
| `generateChapterAsync(id, data)` | POST | `/api/novel/:id/chapter/async` | taskId string |
| `getGenerationStatus(id, taskId)` | GET | `/api/novel/:id/chapter/status/:taskId` | status object |
| `getChapterList(id)` | GET | `/api/novel/:id/chapter/list` | `ChapterListItem[]` |
| `getChapter(id, num)` | GET | `/api/novel/:id/chapter/:num` | `ChapterVO` |
| `getRecentChapters(id, count)` | GET | `/api/novel/:id/chapter/recent` | `ChapterVO[]` |
| `updateChapter(id, num, data)` | PUT | `/api/novel/:id/chapter/:num` | `ChapterVO` |
| `extractSummary(id, data)` | POST | `/api/novel/:id/chapter/extract-summary` | unknown |
| `saveManualChapter(id, data)` | POST | `/api/novel/:id/chapter/manual` | `ChapterVO` |
| `deleteChapter(id, num)` | DELETE | `/api/novel/:id/chapter/:num` | null |
| `getRecycleBin(id)` | GET | `/api/novel/:id/chapter/recycle-bin` | `ChapterVO[]` |
| `restoreChapter(id, chapterId)` | POST | `/api/novel/:id/chapter/:chapterId/restore` | null |
| `hardDeleteChapter(id, chapterId)` | DELETE | `/api/novel/:id/chapter/:chapterId/hard` | null |
| `getChapterVersions(id, num)` | GET | `/api/novel/:id/chapter/:num/versions` | `ChapterVersionVO[]` |
| `rollbackChapter(id, num, verId)` | POST | `/api/novel/:id/chapter/:num/rollback/:verId` | null |
| `getStreamWriteUrl()` | - | `/api/chapter/stream-write` | URL string |

### Character API (`characterApi`)
| Method | HTTP | Endpoint |
|--------|------|----------|
| `createCharacter(id, data)` | POST | `/api/novel/:id/character` |
| `getCharacterList(id)` | GET | `/api/novel/:id/character/list` |
| `getCharacter(id, charId)` | GET | `/api/novel/:id/character/:charId` |
| `updateCharacter(id, charId, data)` | PUT | `/api/novel/:id/character/:charId` |
| `addGrowthNode(id, charId, data)` | POST | `/api/novel/:id/character/:charId/growth` |

### Clue API (`clueApi`)
| Method | HTTP | Endpoint |
|--------|------|----------|
| `createClue(id, data)` | POST | `/api/novel/:id/clue` |
| `getClueList(id)` | GET | `/api/novel/:id/clue/list` |
| `getUnrecycledClues(id)` | GET | `/api/novel/:id/clue/unrecycled` |
| `recycleClue(id, clueId, chapter)` | PUT | `/api/novel/:id/clue/:clueId/recycle` |

### LLM Config API (`llmConfigApi`)
| Method | HTTP | Endpoint |
|--------|------|----------|
| `getConfigList()` | GET | `/api/llm-config/list` |
| `getConfig(id)` | GET | `/api/llm-config/:id` |
| `createConfig(data)` | POST | `/api/llm-config` |
| `updateConfig(id, data)` | PUT | `/api/llm-config/:id` |
| `deleteConfig(id)` | DELETE | `/api/llm-config/:id` |
| `testConnection(data)` | POST | `/api/llm-config/test` |
| `getActiveConfig(usageType)` | GET | `/api/llm-config/active` |

### Rhythm API (`rhythmApi`)
| Method | HTTP | Endpoint |
|--------|------|----------|
| `getRhythmStatus(id)` | GET | `/api/novel/:id/rhythm/status` |
| `getRecommendedStrand(id)` | GET | `/api/novel/:id/rhythm/recommend` |
| `checkViolation(id, type)` | GET | `/api/novel/:id/rhythm/check` |
| `getReadPullTrend(id)` | GET | `/api/novel/:id/rhythm/trend` |
| `getSuggestion(id)` | GET | `/api/novel/:id/rhythm/suggestion` |
| `analyzeReadPull(id, data)` | POST | `/api/novel/:id/rhythm/read-pull` |

### Hook API (`hookApi`)
| Method | HTTP | Endpoint |
|--------|------|----------|
| `analyzeHook(id, num)` | GET | `/api/novel/:id/chapter/:num/hook` |

### Genre Template API (`genreTemplateApi`)
| Method | HTTP | Endpoint |
|--------|------|----------|
| `getTemplateList()` | GET | `/api/genre-template/list` |
| `getTemplatesByGenre(genre)` | GET | `/api/genre-template/by-genre` |
| `getTemplate(id)` | GET | `/api/genre-template/:id` |

### Style Card API (`styleApi`) in `api/style.ts`
| Method | HTTP | Endpoint |
|--------|------|----------|
| `createFromText(data)` | POST | `/api/style-card/create` |
| `createFromFile(file, name)` | POST | `/api/style-card/upload` (multipart) |
| `getUserCards()` | GET | `/api/style-card/list` |
| `getPublicCards(page, size)` | GET | `/api/style-card/public` |
| `getDetail(cardId)` | GET | `/api/style-card/:cardId` |
| `bindNovel(novelId, cardId)` | POST | `/api/style-card/bind` |
| `unbindNovel(novelId)` | POST | `/api/style-card/unbind` |
| `getBoundCardId(novelId)` | GET | `/api/style-card/binding/:novelId` |
| `quickCheck(text, cardId)` | POST | `/api/style-card/check/quick` |
| `deepCheck(text, cardId)` | POST | `/api/style-card/check/deep` |
| `delete(cardId)` | DELETE | `/api/style-card/:cardId` |
| `updatePublic(cardId, isPublic)` | PUT | `/api/style-card/:cardId/public` |

### Streaming/SSE Endpoint
- `POST /api/chapter/stream-write` - SSE streaming chapter generation
  - Request body: `{ novelId, chapterNum, outline, llmConfigId }`
  - Response: SSE stream with `data:` lines for content chunks
  - `event: complete` signals end
- `POST /api/chapter/cancel?novelId=X` - Cancel ongoing stream

---

## 7. PAGE-BY-PAGE UI PATTERNS

### 7.1 Login Page (`/login`)
- **Layout**: Centered card on gradient background (purple)
- **UI Components**: el-tabs (Login/Register), el-form with validation, el-input with prefix icons
- **Actions**: Login -> POST `/api/auth/login`, Register -> POST `/api/auth/register`

### 7.2 Home Page (`/`)
- **Layout**: Max-width 1200px centered
- **UI Components**: 
  - el-card welcome banner with feature highlights (el-row/el-col 3-col grid)
  - CTA buttons: "开始创作" / "我的小说"
  - Stats card: el-row 4-col grid showing novelCount, totalChapters, totalWords, activeNovels
- **Data**: Loads from `novelApi.getNovelList()` on mount

### 7.3 Dashboard (`/dashboard`)
- **Layout**: Full-width with el-select novel picker
- **UI Components**:
  - **Stats cards**: 4-column CSS grid (chapters, words, avg read-pull score, characters)
  - **ECharts line chart**: Read-pull trend (追读力趋势) - smooth area gradient chart
  - **ECharts force graph**: Character relationship graph (人物关系图谱)
  - **el-table**: Recent 10 chapters with view/analyze actions
- **Data Sources**: Parallel loads from `novelApi`, `rhythmApi`, `chapterApi`, `characterApi`
- **Responsive**: Charts container collapses to single column on mobile

### 7.4 Novels List (`/novels`)
- **Layout**: Page header + 3-column card grid
- **UI Components**:
  - el-card per novel with hover shadow
  - el-tag for status (草稿/连载中/已完结)
  - Info display: genre, chapter count, word count, update date
  - Action buttons: 查看详情, 编辑, 删除
  - el-empty state when no novels
- **Data**: `novelApi.getNovelList()`
- **Delete**: ElMessageBox.confirm dialog

### 7.5 Create Novel (`/create`)
- **Layout**: Max-width 900px centered card
- **UI Components**:
  - el-form with validation rules
  - **el-select** for genre (grouped: 男频/女频/通用)
  - **StyleSelector** component (文风卡片 dropdown)
  - **el-collapse** for genre template preview (tropes, pitfalls, archetypes)
  - **el-input textarea** for prompt, totalOutline, worldSetting
  - "AI一键生成" button for auto-generating outline
- **Data Flow**: 
  - Genre change -> loads genre template from `genreTemplateApi.getTemplatesByGenre()`
  - Auto-generate -> `novelApi.autoGenerateOutline()`
  - Submit -> `novelApi.createNovel()`

### 7.6 Novel Detail (`/novel/:novelId`) - MAIN WORKSPACE
- **Layout**: el-row 2-column (8/16 split)

#### Left Column (3 panels):
1. **NovelInfoPanel**: Novel name, status tag, genre, chapter count, word count. Buttons: 编辑, 伏笔, 人物
2. **RhythmPanel**: Strand rhythm status with el-progress bars for Quest/Fire/Constellation ratios, recommended next strand
3. **CharacterPanel**: Compact character list with add button

#### Right Column - ChapterList:
- Toolbar: 回收站, 导出, 极客模式, 手动撰写, AI生成, 图谱, 时间线
- Chapter items: num, title, strand tag, word count, actions (查看/编辑/钩子/删除)

#### Dialogs (7 total in this page):
1. **GenerateChapterDialog** (component): Multi-step chapter generation
   - Step 1: Form (chapterNum, model select, strand type, user intent)
   - Step 2: Outline preview with implementation ideas (radio group)
   - Step 3: Generate (后台生成 or 流式生成)
2. **Manual Write Dialog**: el-form + WangEditor rich text editor
3. **Geek Mode Dialog**: 4-step wizard (el-steps) for external AI workflow
4. **Hook Analysis Dialog**: el-progress bars for 5 hook dimensions + total score tag + suggestion alert
5. **Recycle Bin Dialog**: el-table with restore/hard-delete actions
6. **Graph Dialog**: ECharts force-directed graph for character relationships
7. **Timeline Dialog**: el-form for world time (currentTime, season, weather, days)
8. **Stream Dialog**: Real-time SSE content display with blinking cursor, save/edit/delete/regenerate buttons

### 7.7 Novel Edit (`/novel/:novelId/edit`)
- **Layout**: Max-width 800px, back button + form card
- **UI Components**: el-form with novelName, genre select, status select, totalOutline textarea, worldSetting textarea
- **Actions**: Save -> `novelApi.updateNovel()`

### 7.8 Chapter Detail (`/novel/:novelId/chapter/:chapterNum`)
- **Layout**: Max-width 900px card
- **UI Components**:
  - Header: Back button, chapter title, prev/next navigation, "历史版本" button
  - Info row: word count, plot fingerprint
  - Content: Paragraphs split by double newline, indented
  - **Version History Dialog**: el-table with preview/rollback actions
  - **Version Preview Dialog**: Plain text display

### 7.9 Chapter Edit (`/novel/:novelId/chapter/:chapterNum/edit`)
- **Layout**: Max-width 900px, back button + form card
- **UI Components**:
  - el-form: chapterTitle input, plotFingerprint input
  - **WangEditor** rich text editor (500px height, simple mode)
  - Word count display
- **Actions**: Save -> `chapterApi.updateChapter()`

### 7.10 Characters (`/novel/:novelId/characters`)
- **Layout**: 3-column card grid
- **UI Components**:
  - el-card per character with role tag
  - Character info: description, personality, background
  - **el-timeline** for growth nodes (chapter, title, type, description)
  - Action buttons: 编辑, 添加成长
  - **Create/Edit Dialog**: el-form (name, role select, description, personality, background)
  - **Growth Node Dialog**: el-form (chapterNum, title, growthType select, description)

### 7.11 Clues (`/novel/:novelId/clues`)
- **Layout**: Filter bar + vertical card list
- **UI Components**:
  - Filter: el-select for type (主线伏笔/支线伏笔/暗线), el-select for status (未回收/已回收)
  - el-card per clue with type tag, status tag, description, chapter metadata
  - **Create Dialog**: el-form (title, type, plantedChapter, description)
  - **Recycle Dialog**: el-form (chapter number for recycling)

### 7.12 LLM Config (`/llm-config`)
- **Layout**: Alert + template buttons + 3-column config card grid
- **UI Components**:
  - el-alert with config instructions
  - Quick config template buttons (Ollama, DeepSeek, OpenAI, GLM, Qwen, Moonshot, Wenxin, Embeddings)
  - el-card per config with dropdown menu (edit/test/delete)
  - Config info: model name, API URL (truncated with tooltip), auth type, usage type tag
  - **Add/Edit Dialog**: el-form with configName, modelName, modelType select (chat/embedding), baseUrl textarea, authType select, usageType select, apiKey password, maxTokens/temperature number inputs, isActive switch
  - Test connection button in dialog footer

### 7.13 Style Card Library (`/style-cards`)
- **Layout**: Header + responsive card grid (xs=24, sm=12, md=8, lg=6)
- **UI Components**:
  - el-card per style card with status tag, name, source novel, description, tags, stats (rating, usage count)
  - Action buttons: 使用, 预览, dropdown (toggle public, delete)
  - **Create Dialog**: el-tabs (粘贴文本 / 上传文件)
    - Text tab: name input + textarea (with char count)
    - File tab: name input + el-upload (.txt only, 50MB max)
  - **Preview Dialog**: el-descriptions, style description, stats grid, sample items with annotations, forbidden words tags

### 7.14 404 Page
- el-result with warning icon, "返回首页" and "返回上一页" buttons

---

## 8. REUSABLE COMPONENTS

### `components/Layout.vue`
- Sidebar + main content shell, handles guest route detection

### `components/StyleSelector.vue`
- el-select dropdown for style cards with filterable/clearable
- Loads user cards + public cards, merges and deduplicates
- Shows inline preview (description, stats, sample text) when card selected
- Detail dialog with full card info
- Footer link to create new card

### `components/novel/NovelInfoPanel.vue`
- el-card with novel metadata display
- Emits: edit, manage-clues, manage-chars

### `components/novel/RhythmPanel.vue`
- el-card with strand rhythm metrics
- el-progress bars for consecutive counts
- Ratio display (Quest/Fire/Constellation percentages)
- Recommended strand alert

### `components/novel/CharacterPanel.vue`
- el-card with compact character list
- Emits: add

### `components/novel/ChapterList.vue`
- el-card with toolbar (7 action buttons) + chapter items
- Each chapter: num, title, strand tag, word count, 4 action buttons
- Emits: recycle-bin, export, geek-mode, manual-write, ai-generate, show-graph, show-timeline, view-chapter, edit-chapter, analyze-hook, delete-chapter

### `components/novel/GenerateChapterDialog.vue`
- Multi-step chapter generation dialog
- Model selector, strand type selector, user intent textarea
- Outline preview with radio group for implementation ideas
- Two generation modes: 后台生成 (async polling) and 流式生成 (SSE)
- Exports `GenerateForm` interface

### `components/common/EmptyState.vue`
- Wrapper around el-empty with customizable description and image size

### `components/common/LoadingSpinner.vue`
- Spinning icon + text, customizable size/color/minHeight

---

## 9. UI PATTERNS SUMMARY

### Form Patterns
- el-form with el-form-item, label-width, validation rules
- el-input, el-input-number, el-select, el-switch, el-input type="textarea"
- Form validation via `formRef.validate()`
- Loading state on submit buttons

### Dialog Patterns
- el-dialog with v-model, title, width
- destroy-on-close for forms
- Footer slot with cancel/confirm buttons
- Loading states on confirm buttons

### Table Patterns
- el-table with el-table-column
- Scoped slots for custom rendering
- v-loading directive

### Card Patterns
- el-card with header slot, shadow="hover"
- Grid layout (el-row/el-col) for card lists

### Chart Patterns
- ECharts initialized via ref
- Responsive resize on window resize
- Proper dispose on unmount

### Notification Patterns
- ElMessage.success/error/warning/info
- ElMessageBox.confirm for destructive actions
- v-loading directive for async states

### Navigation Patterns
- Back buttons with ArrowLeft icon
- Next/prev chapter navigation
- Breadcrumb-like page headers

---

## 10. TYPE DEFINITIONS (types/index.ts)

### Core Types
- `ApiResponse<T>`: `{ code, message, data }`
- `UserVO`: userId, username, email, nickname, role, status, token
- `NovelVO`: novelId, novelName, genre, totalOutline, worldSetting, status(0/1/2), chapterCount, wordCount, timestamps
- `ChapterVO`: chapterId, novelId, chapterNum, chapterTitle, content, wordCount, strandType, plotFingerprint, summary, status
- `ChapterListItem`: chapterId, chapterNum, chapterTitle, wordCount, strandType, status
- `CharacterVO`: characterId, novelId, name, role(主角/配角/反派/龙套), description, personality, background, growthNodes[]
- `GrowthNode`: nodeId, chapterNum, title, description, growthType(能力提升/性格转变/关系变化)
- `ClueVO`: clueId, novelId, title, description, clueType(主线伏笔/支线伏笔/暗线), plantedChapter, recycleChapter, status(0/1)
- `LlmConfigVO`: configId, configName, provider, apiKey, apiBaseUrl, modelName, authType, temperature, maxTokens, topP, isActive, usageType
- `RhythmStatusVO`: lastStrand, questConsecutiveCount, fireGapCount, constellationGapCount, questRatio, fireRatio, constellationRatio
- `ReadPullTrendVO`: averageScore, trend, scores[], chapterNumbers[], readPullScores[]
- `HookAnalysisVO`: openingAttraction, suspenseSetup, emotionTrigger, endingHook, coolPointDensity (all 1-10), pullLevel, totalScore, suggestion
- `NovelGraphVO`: nodes[], edges[]
- `TimelineVO`: clockId, novelId, currentTime, currentDate, season, weather, chapterDay, totalDays
- `GenreTemplateVO`: code, name, description, tropes[], pitfalls[], coreThemes[], characterArchetypes{}, plotPatterns[]
- `ChapterVersionVO`: versionId, chapterNum, content, createTime

### Style Types (types/style.ts)
- `StyleCard`: cardId, name, sourceNovel, rules (rhythm/dialogue/description/emotion/structure/forbidden/preferred), styleDescription, generationTemplate, samplesJson, statsJson, tags, isPublic, usageCount, avgRating, status
- `StyleMetrics`: avgSentenceLength, shortSentenceRatio, dialogueRatio, avgParagraphLength, etc.
- `StyleCheckResult`: score, pass, issues[], suggestions[]

---

## 11. UTILITY FUNCTIONS (utils/format.ts)

- `formatWordCount(count)`: "1.2万字" or "500字"
- `formatDate(dateStr)`: Chinese locale date
- `formatDateTime(dateStr)`: Chinese locale datetime
- `getNovelStatusType(status)`: Element Plus tag type
- `getNovelStatusText(status)`: "草稿"/"连载中"/"已完结"
- `getStrandType(strand)`: Element Plus tag type for quest/fire/constellation
- `formatStrand(strand)`: "Quest主线"/"Fire感情线"/"Constellation世界观"
- `getRoleType(role)`: Element Plus tag type for character roles
- `debounce(fn, delay)` / `throttle(fn, delay)`

---

## 12. KEY TECHNICAL DETAILS FOR ANDROID REPLICA

1. **Base URL**: All API calls go to `/api` prefix (proxied to localhost:8080 in dev)
2. **Auth**: Bearer token in Authorization header, stored in localStorage
3. **Timeout**: 180 seconds for API calls (LLM operations are slow)
4. **SSE Streaming**: POST to `/api/chapter/stream-write`, read response as stream, parse `data:` lines
5. **File Upload**: Multipart form data for style card file upload
6. **Blob Download**: Novel export returns blob, needs file save on Android
7. **No pagination on most lists** - all data loaded at once (novels, chapters, characters, clues)
8. **Genre options**: 玄幻, 仙侠, 都市, 历史, 科幻, 游戏, 末世, 言情, 悬疑
9. **Strand types**: quest, fire, constellation
10. **Novel statuses**: 0=draft, 1=serializing, 2=completed
11. **Character roles**: 主角, 配角, 反派, 龙套
12. **Clue types**: 主线伏笔, 支线伏笔, 暗线
13. **Growth types**: 能力提升, 性格转变, 关系变化
