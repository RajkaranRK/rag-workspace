-- Schema for the RAG vector store. Run once against ragdb after the
-- pgvector container is up:
--   docker exec -i rag-postgres psql -U raguser -d ragdb < postgres/schema.sql
-- (or: psql postgresql://raguser:ragpassword@localhost:5432/ragdb -f postgres/schema.sql)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_name TEXT        NOT NULL,
    page_number   INTEGER     NOT NULL,
    chunk_number  INTEGER     NOT NULL,
    content       TEXT        NOT NULL,
    embedding     VECTOR(768) NOT NULL
);

-- Approximate-nearest-neighbour index for cosine distance (<=>).
CREATE INDEX IF NOT EXISTS document_chunks_embedding_idx
    ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Book chunks use positive chunk_number (1..N) and the book's file name.
-- Answers learned from the web use document_name = 'web-learned' and a
-- negative chunk_number, so they never collide with book chunk IDs used
-- by the evaluation endpoints.

-- Questions the book could not answer (the learning pipeline's phase-1 table).
-- The demo app also creates this automatically on startup; this is the
-- canonical definition. Lifecycle: PENDING -> GENERATED -> STORED.
CREATE TABLE IF NOT EXISTS unanswered_queries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question        TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'PENDING',
    answer          TEXT,
    source_event_id TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
