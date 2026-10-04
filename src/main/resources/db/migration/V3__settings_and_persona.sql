-- V3: Create settings table and seed with default GENTLE persona and EN language

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
