CREATE TABLE IF NOT EXISTS journal_summary (
    id BIGSERIAL PRIMARY KEY,
    period_type VARCHAR(20) NOT NULL, -- 'WEEK', 'MONTH', 'YEAR'
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    headline VARCHAR(255),
    body TEXT NOT NULL,
    themes TEXT[] DEFAULT '{}',
    stats JSONB NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'AI', -- 'AI', 'STATS_ONLY'
    is_final BOOLEAN NOT NULL DEFAULT FALSE,
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_journal_summary_type_start UNIQUE (period_type, period_start)
);

CREATE INDEX IF NOT EXISTS idx_journal_summary_type_start ON journal_summary (period_type, period_start DESC);
