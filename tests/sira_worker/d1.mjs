// Testler için Cloudflare D1 benzeri: Node 22'nin içindeki SQLite (node:sqlite) üstünde prepare/bind/first/all/run/batch.
// Gerçek schema.sql uygulanır; SQL'ler D1'e gidenlerle aynıdır.
import { DatabaseSync } from "node:sqlite";

class Statement {
  constructor(db, sql, params = []) {
    this.db = db;
    this.sql = sql;
    this.params = params;
  }
  bind(...params) {
    return new Statement(this.db, this.sql, params);
  }
  _stmt() {
    return this.db.prepare(this.sql);
  }
  async first() {
    const row = this._stmt().get(...this.params);
    return row === undefined ? null : { ...row };
  }
  async all() {
    const rows = this._stmt().all(...this.params).map((r) => ({ ...r }));
    return { results: rows, success: true, meta: {} };
  }
  _runSync() {
    const r = this._stmt().run(...this.params);
    return { success: true, meta: { changes: Number(r.changes), last_row_id: Number(r.lastInsertRowid) } };
  }
  async run() {
    return this._runSync();
  }
}

export class D1 {
  constructor() {
    this.db = new DatabaseSync(":memory:");
    this.db.exec("PRAGMA foreign_keys = ON");
  }
  exec(sql) {
    this.db.exec(sql);
  }
  prepare(sql) {
    return new Statement(this.db, sql);
  }
  async batch(stmts) {
    this.db.exec("BEGIN");
    try {
      const out = stmts.map((s) => s._runSync());
      this.db.exec("COMMIT");
      return out;
    } catch (e) {
      this.db.exec("ROLLBACK");
      throw e;
    }
  }
}
