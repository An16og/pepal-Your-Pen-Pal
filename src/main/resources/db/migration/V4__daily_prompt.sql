-- V4: Create daily_prompt table for storing generated and fallback questions

CREATE TABLE IF NOT EXISTS daily_prompt (
    prompt_date DATE PRIMARY KEY,
    questions JSONB NOT NULL,
    source TEXT NOT NULL CHECK (source IN ('GENERATED', 'FALLBACK')),
    reshuffle_count SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_daily_prompt_created_at
    ON daily_prompt (created_at DESC);
