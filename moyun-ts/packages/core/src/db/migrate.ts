import type Database from 'better-sqlite3';

export function runMigrations(db: Database.Database) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS novels (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      genre TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'draft',
      creation_mode TEXT DEFAULT 'classic',
      total_outline TEXT,
      world_setting TEXT,
      story_tags TEXT,
      tone_tags TEXT,
      taboo_list TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS chapters (
      id TEXT PRIMARY KEY,
      novel_id TEXT NOT NULL REFERENCES novels(id),
      chapter_num INTEGER NOT NULL,
      title TEXT,
      content TEXT,
      outline TEXT,
      plot_fingerprint TEXT,
      strand_type TEXT,
      word_count INTEGER DEFAULT 0,
      is_deleted INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS chapter_versions (
      id TEXT PRIMARY KEY,
      chapter_id TEXT NOT NULL REFERENCES chapters(id),
      version_num INTEGER NOT NULL,
      content TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS chapter_summaries (
      chapter_id TEXT PRIMARY KEY REFERENCES chapters(id),
      fingerprint TEXT,
      keywords TEXT,
      characters TEXT,
      locations TEXT,
      relations TEXT
    );

    CREATE TABLE IF NOT EXISTS arcs (
      id TEXT PRIMARY KEY,
      novel_id TEXT NOT NULL REFERENCES novels(id),
      arc_index INTEGER NOT NULL,
      name TEXT NOT NULL,
      outline TEXT,
      strand_type TEXT,
      rhythm_intensity INTEGER DEFAULT 5
    );

    CREATE TABLE IF NOT EXISTS characters (
      id TEXT PRIMARY KEY,
      novel_id TEXT NOT NULL REFERENCES novels(id),
      name TEXT NOT NULL,
      role TEXT NOT NULL,
      description TEXT,
      personality TEXT,
      background TEXT
    );

    CREATE TABLE IF NOT EXISTS character_growth (
      id TEXT PRIMARY KEY,
      character_id TEXT NOT NULL REFERENCES characters(id),
      chapter_num INTEGER NOT NULL,
      title TEXT NOT NULL,
      type TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS character_states (
      character_id TEXT PRIMARY KEY REFERENCES characters(id),
      state TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS clues (
      id TEXT PRIMARY KEY,
      novel_id TEXT NOT NULL REFERENCES novels(id),
      title TEXT NOT NULL,
      type TEXT NOT NULL,
      description TEXT,
      bury_chapter INTEGER,
      recycle_chapter INTEGER,
      is_recycled INTEGER DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS story_states (
      novel_id TEXT PRIMARY KEY REFERENCES novels(id),
      state TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS event_nodes (
      id TEXT PRIMARY KEY,
      novel_id TEXT NOT NULL REFERENCES novels(id),
      chapter_num INTEGER NOT NULL,
      data TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS llm_configs (
      id TEXT PRIMARY KEY,
      provider TEXT NOT NULL,
      model TEXT NOT NULL,
      api_key TEXT,
      base_url TEXT,
      auth_type TEXT DEFAULT 'api_key',
      usage_type TEXT DEFAULT 'general',
      temperature REAL DEFAULT 0.7,
      is_active INTEGER DEFAULT 1
    );

    CREATE TABLE IF NOT EXISTS style_cards (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      metrics TEXT,
      sample_text TEXT,
      is_public INTEGER DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS novel_style_bindings (
      novel_id TEXT NOT NULL REFERENCES novels(id),
      style_card_id TEXT NOT NULL REFERENCES style_cards(id),
      PRIMARY KEY (novel_id, style_card_id)
    );

    CREATE TABLE IF NOT EXISTS vectors (
      id TEXT PRIMARY KEY,
      novel_id TEXT NOT NULL,
      chapter_num INTEGER NOT NULL,
      content TEXT,
      keywords TEXT,
      priority INTEGER DEFAULT 5,
      embedding TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS style_samples (
      id TEXT PRIMARY KEY,
      card_id TEXT NOT NULL REFERENCES style_cards(id),
      sample_type TEXT NOT NULL,
      content TEXT NOT NULL,
      annotation TEXT,
      sort_order INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL
    );
  `);

  // Add new columns to existing tables (idempotent)
  const alterStatements = [
    'ALTER TABLE style_cards ADD COLUMN rhythm_rules TEXT',
    'ALTER TABLE style_cards ADD COLUMN dialogue_rules TEXT',
    'ALTER TABLE style_cards ADD COLUMN description_rules TEXT',
    'ALTER TABLE style_cards ADD COLUMN emotion_rules TEXT',
    'ALTER TABLE style_cards ADD COLUMN structure_rules TEXT',
    'ALTER TABLE style_cards ADD COLUMN forbidden_rules TEXT',
    'ALTER TABLE style_cards ADD COLUMN preferred_rules TEXT',
    'ALTER TABLE style_cards ADD COLUMN style_description TEXT',
    'ALTER TABLE style_cards ADD COLUMN generation_template TEXT',
    'ALTER TABLE style_cards ADD COLUMN samples_json TEXT',
    'ALTER TABLE style_cards ADD COLUMN stats_json TEXT',
    'ALTER TABLE style_cards ADD COLUMN source_novel TEXT',
    'ALTER TABLE style_cards ADD COLUMN author_name TEXT',
    'ALTER TABLE style_cards ADD COLUMN tags TEXT',
    'ALTER TABLE style_cards ADD COLUMN status INTEGER DEFAULT 1',
    'ALTER TABLE llm_configs ADD COLUMN config_name TEXT',
    'ALTER TABLE character_growth ADD COLUMN description TEXT',
  ];

  for (const stmt of alterStatements) {
    try { db.exec(stmt); } catch { /* column already exists */ }
  }
}
