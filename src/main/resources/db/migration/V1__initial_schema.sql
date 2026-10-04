CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Journal Entries Table
CREATE TABLE IF NOT EXISTS journal_entry (
    id BIGSERIAL PRIMARY KEY,
    entry_type VARCHAR(32) NOT NULL DEFAULT 'FREEFORM',
    entry_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    energy VARCHAR(16) NOT NULL DEFAULT 'HIGH',
    mood VARCHAR(16) NOT NULL DEFAULT 'GOOD',
    body TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_journal_entry_daily_date
    ON journal_entry (entry_date)
    WHERE entry_type = 'DAILY_PROMPT';

CREATE INDEX IF NOT EXISTS idx_journal_entry_date_order
    ON journal_entry (entry_date DESC, created_at DESC, id DESC);

-- 2. Embeddings Table
CREATE TABLE IF NOT EXISTS entry_embedding (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES journal_entry(id) ON DELETE CASCADE,
    embedding VECTOR(768) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entry_embedding_entry_id
    ON entry_embedding (entry_id);

CREATE INDEX IF NOT EXISTS idx_entry_embedding_vector
    ON entry_embedding USING hnsw (embedding vector_cosine_ops);
