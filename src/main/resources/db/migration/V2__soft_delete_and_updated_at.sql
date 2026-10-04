-- V2: Add updated_at, deleted_at, partial unique index, and trash index

ALTER TABLE journal_entry
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

DROP INDEX IF EXISTS idx_journal_entry_daily_date;

CREATE UNIQUE INDEX idx_journal_entry_daily_date
    ON journal_entry (entry_date)
    WHERE entry_type = 'DAILY_PROMPT' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_journal_entry_deleted_at
    ON journal_entry (deleted_at DESC)
    WHERE deleted_at IS NOT NULL;
