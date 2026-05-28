import { sqliteTable, text, integer, real } from 'drizzle-orm/sqlite-core';

// ===== 小说 =====
export const novels = sqliteTable('novels', {
  id: text('id').primaryKey(),
  title: text('title').notNull(),
  genre: text('genre').notNull(),
  status: text('status').notNull().default('draft'),
  creationMode: text('creation_mode').default('classic'),
  totalOutline: text('total_outline'),
  worldSetting: text('world_setting'),
  storyTags: text('story_tags'),
  toneTags: text('tone_tags'),
  tabooList: text('taboo_list'),
  createdAt: integer('created_at', { mode: 'timestamp' }).notNull(),
  updatedAt: integer('updated_at', { mode: 'timestamp' }).notNull(),
});

// ===== 章节 =====
export const chapters = sqliteTable('chapters', {
  id: text('id').primaryKey(),
  novelId: text('novel_id').notNull().references(() => novels.id),
  chapterNum: integer('chapter_num').notNull(),
  title: text('title'),
  content: text('content'),
  outline: text('outline'),
  plotFingerprint: text('plot_fingerprint'),
  strandType: text('strand_type'),
  wordCount: integer('word_count').default(0),
  isDeleted: integer('is_deleted', { mode: 'boolean' }).default(false),
  createdAt: integer('created_at', { mode: 'timestamp' }).notNull(),
  updatedAt: integer('updated_at', { mode: 'timestamp' }).notNull(),
});

// ===== 章节版本 =====
export const chapterVersions = sqliteTable('chapter_versions', {
  id: text('id').primaryKey(),
  chapterId: text('chapter_id').notNull().references(() => chapters.id),
  versionNum: integer('version_num').notNull(),
  content: text('content').notNull(),
  createdAt: integer('created_at', { mode: 'timestamp' }).notNull(),
});

// ===== 章节摘要 =====
export const chapterSummaries = sqliteTable('chapter_summaries', {
  chapterId: text('chapter_id').primaryKey().references(() => chapters.id),
  fingerprint: text('fingerprint'),
  keywords: text('keywords'),
  characters: text('characters'),
  locations: text('locations'),
  relations: text('relations'),
});

// ===== 卷/弧 =====
export const arcs = sqliteTable('arcs', {
  id: text('id').primaryKey(),
  novelId: text('novel_id').notNull().references(() => novels.id),
  arcIndex: integer('arc_index').notNull(),
  name: text('name').notNull(),
  outline: text('outline'),
  strandType: text('strand_type'),
  rhythmIntensity: integer('rhythm_intensity').default(5),
});

// ===== 角色 =====
export const characters = sqliteTable('characters', {
  id: text('id').primaryKey(),
  novelId: text('novel_id').notNull().references(() => novels.id),
  name: text('name').notNull(),
  role: text('role').notNull(),
  description: text('description'),
  personality: text('personality'),
  background: text('background'),
});

// ===== 角色成长 =====
export const characterGrowth = sqliteTable('character_growth', {
  id: text('id').primaryKey(),
  characterId: text('character_id').notNull().references(() => characters.id),
  chapterNum: integer('chapter_num').notNull(),
  title: text('title').notNull(),
  type: text('type').notNull(),
  description: text('description'),
});

// ===== 角色状态 =====
export const characterStates = sqliteTable('character_states', {
  characterId: text('character_id').primaryKey().references(() => characters.id),
  state: text('state').notNull(),
});

// ===== 线索 =====
export const clues = sqliteTable('clues', {
  id: text('id').primaryKey(),
  novelId: text('novel_id').notNull().references(() => novels.id),
  title: text('title').notNull(),
  type: text('type').notNull(),
  description: text('description'),
  buryChapter: integer('bury_chapter'),
  recycleChapter: integer('recycle_chapter'),
  isRecycled: integer('is_recycled', { mode: 'boolean' }).default(false),
});

// ===== 故事状态 =====
export const storyStates = sqliteTable('story_states', {
  novelId: text('novel_id').primaryKey().references(() => novels.id),
  state: text('state').notNull(),
});

// ===== 事件图 =====
export const eventNodes = sqliteTable('event_nodes', {
  id: text('id').primaryKey(),
  novelId: text('novel_id').notNull().references(() => novels.id),
  chapterNum: integer('chapter_num').notNull(),
  data: text('data').notNull(),
});

// ===== LLM配置 =====
export const llmConfigs = sqliteTable('llm_configs', {
  id: text('id').primaryKey(),
  provider: text('provider').notNull(),
  model: text('model').notNull(),
  apiKey: text('api_key'),
  baseUrl: text('base_url'),
  authType: text('auth_type').default('api_key'),
  usageType: text('usage_type').default('general'),
  temperature: real('temperature').default(0.7),
  isActive: integer('is_active', { mode: 'boolean' }).default(true),
  configName: text('config_name'),
});

// ===== 风格卡 =====
export const styleCards = sqliteTable('style_cards', {
  id: text('id').primaryKey(),
  name: text('name').notNull(),
  metrics: text('metrics'),
  sampleText: text('sample_text'),
  isPublic: integer('is_public', { mode: 'boolean' }).default(false),
  rhythmRules: text('rhythm_rules'),
  dialogueRules: text('dialogue_rules'),
  descriptionRules: text('description_rules'),
  emotionRules: text('emotion_rules'),
  structureRules: text('structure_rules'),
  forbiddenRules: text('forbidden_rules'),
  preferredRules: text('preferred_rules'),
  styleDescription: text('style_description'),
  generationTemplate: text('generation_template'),
  samplesJson: text('samples_json'),
  statsJson: text('stats_json'),
  sourceNovel: text('source_novel'),
  authorName: text('author_name'),
  tags: text('tags'),
  status: integer('status').default(1),
});

// ===== 小说-风格卡绑定 =====
export const novelStyleBindings = sqliteTable('novel_style_bindings', {
  novelId: text('novel_id').notNull().references(() => novels.id),
  styleCardId: text('style_card_id').notNull().references(() => styleCards.id),
});

// ===== 向量存储 =====
export const vectors = sqliteTable('vectors', {
  id: text('id').primaryKey(),
  novelId: text('novel_id').notNull(),
  chapterNum: integer('chapter_num').notNull(),
  content: text('content'),
  keywords: text('keywords'),
  priority: integer('priority').default(5),
  embedding: text('embedding').notNull(),  // base64 encoded Float32Array
  createdAt: integer('created_at', { mode: 'timestamp' }).notNull(),
});

// ===== 文风样本 =====
export const styleSamples = sqliteTable('style_samples', {
  id: text('id').primaryKey(),
  cardId: text('card_id').notNull().references(() => styleCards.id),
  sampleType: text('sample_type').notNull(),
  content: text('content').notNull(),
  annotation: text('annotation'),
  sortOrder: integer('sort_order').default(0),
  createdAt: integer('created_at', { mode: 'timestamp' }).notNull(),
});
