import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { resolve } from 'node:path';
import { homedir } from 'node:os';
import { mkdirSync } from 'node:fs';
import * as schema from './schema.js';
import { runMigrations } from './migrate.js';

const DB_DIR = resolve(homedir(), '.moyun');
const DB_PATH = resolve(DB_DIR, 'data.db');

let _db: ReturnType<typeof drizzle> | null = null;

export function getDb() {
  if (!_db) {
    mkdirSync(DB_DIR, { recursive: true });
    const sqlite = new Database(DB_PATH);
    sqlite.pragma('journal_mode = WAL');
    sqlite.pragma('foreign_keys = ON');
    runMigrations(sqlite);
    _db = drizzle(sqlite, { schema });
  }
  return _db;
}

export type Db = ReturnType<typeof getDb>;
export { schema };
