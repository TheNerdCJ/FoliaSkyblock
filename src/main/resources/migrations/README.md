# FoliaSkyblock SQL migrations

Versioned schema changes ship as `v{N}_{snake_name}.sql` under this folder. They are loaded at startup by `MigrationScriptRegistry` (after `DatabaseManager.createTables()`).

## Naming

- **File:** `v14_island_worth_grid_index.sql`
- **Version:** integer prefix before the first underscore (`14`)
- **Name:** remainder of the filename (used in logs and registry keys)

Prefer **one logical change per file** and **one statement per file** when possible (easier rollback notes for hosts).

## Authoring rules

1. Use SQLite-compatible syntax (`CREATE INDEX IF NOT EXISTS`, `ALTER TABLE` with try/catch in Java only when SQL cannot express idempotency).
2. Do not duplicate indexes already created in `DatabaseManager.createTables()` unless migrating legacy installs.
3. Bump `N` monotonically; never reuse a version number.
4. Test with `mvn test -Dtest=DatabaseCriticalFlowsTest` after adding a migration.

## Host backup workflow

1. `/isadmin flushwrites` — drain coalesced island + shop purchase queues.
2. `/isadmin checkpoint` — SQLite `wal_checkpoint(TRUNCATE)` when WAL is enabled.
3. Copy `plugins/FoliaSkyblock/skyblock.db` only (avoid copying `-wal`/`-shm` immediately after TRUNCATE).

## Current scripts

| Version | File | Purpose |
|---------|------|---------|
| 14 | `v14_island_worth_grid_index.sql` | Worth/grid query index |
| 15 | `v15_chest_shops_chunk_index.sql` | Chest shop chunk lookup index |