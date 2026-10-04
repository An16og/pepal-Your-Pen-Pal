# pepal Journal

A 100% local, offline, physical-style AI journal web app. Built with Spring Boot 3, Spring AI, PostgreSQL (pgvector), Flyway, and vanilla HTML/CSS/JS.

## Features

- **2-Table Architecture**: Normalized PostgreSQL storage separating `journal_entry` metadata from dense `entry_embedding` 768-dimensional vectors.
- **Personality System & Settings**: Persona presets (`GENTLE`, `PLAYFUL`, `BLUNT`, `COACH`, `CUSTOM`) and language options (`EN`, `HINGLISH`) composed dynamically with a strict, non-negotiable Safety Layer.
- **Dynamic Mood-Aware Daily Prompts**: 3 AI-tailored reflection questions with deterministic static fallback and per-date reshuffling.
- **Full Entry Lifecycle**: Create, edit (`PUT /api/entries/{id}`), soft-delete (`DELETE /api/entries/{id}`), trash listing (`GET /api/trash`), conflict-aware restore (`POST /api/entries/{id}/restore`), permanent delete, and daily automated purge job.
- **Max 1 Daily Reflection per Day**: Prompted with 3 questions. Enforced via partial unique index and Spring Boot service validation.
- **Freeform Journaling**: Add unlimited spontaneous entries without prompts whenever you like.
- **Manual Date Picker & Live Clocks**: Keep a live second-by-second clock on your page, or manually change the date to backfill missed days.
- **Mood & Energy Tracking**: Select Energy (`High` / `Low`) and Mood (`Good` / `Bad`) with every entry. Badges appear in history and are embedded for semantic RAG queries.
- **Side Drawer & Tabs**: Browse past entries and query the grounded RAG bot in dedicated tabs or via a quick-access side drawer.
- **Local RAG Bot**: Queries local Ollama (`llama3.2`) using pgvector cosine similarity search (`<=>`) grounded strictly in your personal entries and states.

## Tech Stack

- **Backend**: Spring Boot 3.4.4, Java 17, Spring JDBC (`JdbcTemplate`), Flyway Migrations
- **AI Integration**: Spring AI (Ollama `llama3.2` chat model and `nomic-embed-text` 768-dim embeddings)
- **Database**: PostgreSQL 16 with `pgvector` extension and HNSW cosine index
- **Frontend**: Single Page Application with Vanilla HTML5, CSS3, and JavaScript Fetch API

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.
- *(Optional for bare-metal dev)*: Java 17+, Maven, and local Ollama.

## Quickstart (1-Click Containerized Stack)

The entire application stack (Spring Boot app, PostgreSQL with pgvector, Ollama, and automated model puller) runs in Docker with zero manual setup.

### On Windows
- **Start**: Double-click `start.bat` (or run `.\start.bat` in PowerShell/CMD).
- **Stop**: Double-click `stop.bat` (or run `.\stop.bat`).

### On macOS / Linux
```bash
chmod +x start.sh stop.sh
./start.sh
```
To stop the services:
```bash
./stop.sh
```

### Manual Docker Compose
```bash
docker compose up -d --build
```

Once started, the application automatically launches and is accessible at [http://localhost:8080](http://localhost:8080). All entries, embeddings, and downloaded AI models persist across restarts in Docker named volumes (`pepal_data`, `pepal_ollama`).

## REST API

### Entries & Trash
- `GET /api/entries` — List all active entries (newest first).
- `GET /api/entries/{id}` — Get active entry by ID.
- `POST /api/entries` — Save new entry.
- `PUT /api/entries/{id}` — Edit body, energy, mood (date/type immutable).
- `DELETE /api/entries/{id}` — Soft delete (move to trash).
- `GET /api/trash` — List trashed entries with days remaining.
- `POST /api/entries/{id}/restore` — Restore entry from trash (409 on daily conflict).
- `DELETE /api/entries/{id}/permanent` — Permanently delete trashed entry.
- `DELETE /api/trash` — Empty the trash.

### Daily Prompts
- `GET /api/prompts/today?date=YYYY-MM-DD` — Get or generate today's 3 questions.
- `POST /api/prompts/reshuffle?date=YYYY-MM-DD` — Reshuffle today's prompt questions.

### Settings & Persona
- `GET /api/settings` — Get current settings and list of presets.
- `PUT /api/settings` — Update persona preset, custom text, and language.
- `GET /api/settings/preview?preset=...&language=...` — Preview composed system prompt.

### AI RAG Chat
- `POST /api/chat` — Ask the RAG companion grounded questions about your journal.

## Changelog & Migration Notes

### Project Rename (USHER to pepal)
- The project has been renamed from USHER to **pepal**.
- **Configuration Prefix**: Configuration properties have migrated from `usher.*` to `otto.*` (e.g. `otto.trash.retention-days`, `otto.prompts.temperature`). A temporary fallback for legacy `usher.*` properties is supported with an automatic startup deprecation warning.
- **Database Compatibility**: Existing databases created under the legacy database name `usher` continue to be supported seamlessly with automatic connection fallback. New docker-compose installations default to `pepal`. To migrate an existing PostgreSQL database to the new name:
  ```sql
  ALTER DATABASE usher RENAME TO pepal;
  ALTER USER usher RENAME TO pepal;
  ```
