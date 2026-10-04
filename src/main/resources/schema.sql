CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Journal Entries Table
CREATE TABLE IF NOT EXISTS journal_entry (
    id BIGSERIAL PRIMARY KEY,
    entry_type VARCHAR(32) NOT NULL DEFAULT 'FREEFORM', -- 'DAILY_PROMPT' or 'FREEFORM'
    entry_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ NULL,
    energy VARCHAR(16) NOT NULL DEFAULT 'HIGH',        -- 'HIGH' or 'LOW'
    mood VARCHAR(16) NOT NULL DEFAULT 'GOOD',          -- 'GOOD' or 'BAD'
    body TEXT NOT NULL
);

-- Partial unique index ensuring max ONE active Daily Reflection entry per date
CREATE UNIQUE INDEX IF NOT EXISTS idx_journal_entry_daily_date
    ON journal_entry (entry_date)
    WHERE entry_type = 'DAILY_PROMPT' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_journal_entry_date_order
    ON journal_entry (entry_date DESC, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_journal_entry_deleted_at
    ON journal_entry (deleted_at DESC)
    WHERE deleted_at IS NOT NULL;

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

-- 3. Settings Table
CREATE TABLE IF NOT EXISTS settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    persona_preset TEXT NOT NULL DEFAULT 'GENTLE'
        CHECK (persona_preset IN ('GENTLE', 'PLAYFUL', 'BLUNT', 'COACH', 'CUSTOM')),
    custom_persona TEXT NULL
        CHECK (custom_persona IS NULL OR char_length(custom_persona) <= 500),
    language TEXT NOT NULL DEFAULT 'EN'
        CHECK (language IN ('EN', 'HINGLISH')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO settings (id, persona_preset, custom_persona, language, updated_at)
VALUES (1, 'GENTLE', NULL, 'EN', now())
ON CONFLICT (id) DO NOTHING;

-- 4. Daily Prompts Table
CREATE TABLE IF NOT EXISTS daily_prompt (
    prompt_date DATE PRIMARY KEY,
    questions JSONB NOT NULL,
    source TEXT NOT NULL CHECK (source IN ('GENERATED', 'FALLBACK')),
    reshuffle_count SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_daily_prompt_created_at
    ON daily_prompt (created_at DESC);
