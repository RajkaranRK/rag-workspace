# rag-workspace

A local, fully offline **Retrieval-Augmented Generation (RAG)** workspace. Ask questions about a system-design book (`documents/system-design-book.pdf`) and get grounded answers with sources, using only models running on your own machine. When the book can't answer, the system records the question and can later generate an answer with the local LLM, embed it, and learn it into the vector store — all offline.

## Projects at a glance

| Folder | Tech | Port | Use case |
|---|---|---|---|
| [`postgres/`](postgres) | Docker Compose, PostgreSQL 16 + pgvector | 5432 | Vector store (`document_chunks`) + the learning queue (`unanswered_queries`) |
| [`kafka/`](kafka) | Docker Compose, Apache Kafka 3.9 (KRaft) | 9092 | Event bus for the learning pipeline (topics `unanswered-queries`, `learned-answers`) |
| [`demo/`](demo) | Java 17, Spring Boot 3.2, PDFBox, JDBC, Spring Kafka | 8080 | **The core RAG application**: ingestion, vector search, reranking, answer generation, evaluation, agentic RAG, and the learning pipeline (queue + generate API + consumers) |
| [`reranker-service/`](reranker-service) | Python, FastAPI, sentence-transformers | 8001 | Second-stage reranker that reorders search results with `BAAI/bge-reranker-v2-m3` |
| [`embedding-demo/`](embedding-demo) | Python (stdlib), Ollama | n/a (CLI) | Starter local tool-using agent in the terminal (list/read project files) |
| [`embedding-demo/spring-agent/`](embedding-demo/spring-agent) | Java 17, Spring Boot 3.5, Spring AI 1.1, WebFlux, Spring Kafka | 8082 | HTTP chat agent; reads project files, calls the RAG API, and records unanswered questions to Kafka (learning phase 1) |
| [`documents/`](documents) | PDF | n/a | Source material (the system design book, ~23 MB) |

## How the pieces fit together

```
INGESTION (one time)
  documents/system-design-book.pdf
    → demo: PdfDocumentReader → DocumentChunker (1000 chars / 200 overlap)
          → embeddings via Ollama (nomic-embed-text, 768-dim)
          → postgres (document_chunks)

QUERY
  question → demo /api/v1/rag/ask
           → embed question → pgvector search (top-k=5, cosine, threshold 0.70)
           → reranker-service /rerank (keep final top 3)
           → Ollama qwen3:8b writes the answer + sources

AGENT
  question → spring-agent :8082 /api/agent/chat
           → (qwen3:8b decides) → systemDesignRag tool → demo /api/v1/rag/agent/ask
                                 └► ProjectTools (list/read local files)

LEARNING (two-phase, async — see docs/learning-pipeline.md)
  Phase 1  book can't answer → spring-agent publishes to Kafka "unanswered-queries"
                             → demo consumes → INSERT unanswered_queries (PENDING)
  Phase 2  POST /api/v1/learning/unanswered/{id}/generate
                             → demo: qwen3:8b answer → Kafka "learned-answers"
                             → demo consumes → chunk + embed + store → STORED
                             → next time, the book lookup serves it
```

External requirement for everything: **[Ollama](https://ollama.com)** on `localhost:11434` with `nomic-embed-text` (768-dim embeddings) and `qwen3:8b` (answers, agent tool calling, learned-answer generation).

---

## 1. `postgres/` — vector store + learning queue

`docker-compose.yml` starts `pgvector/pgvector:pg16` as container `rag-postgres`.

- Database `ragdb`, user `raguser`, password `ragpassword` (local dev defaults), port `5432`
- Persistent volume `postgres_data`, health check via `pg_isready`
- Tables: `document_chunks` (book + learned chunks with their 768-dim embeddings) and `unanswered_queries` (the learning queue). Canonical DDL is in [`postgres/schema.sql`](postgres/schema.sql); `unanswered_queries` is also auto-created by `demo` on startup.

```bash
cd postgres && docker compose up -d
docker exec -i rag-postgres psql -U raguser -d ragdb < schema.sql   # once
```

## 2. `kafka/` — event bus

`docker-compose.yml` starts `apache/kafka:3.9.0` (KRaft, no ZooKeeper) as container `rag-kafka` on port `9092`. Ephemeral by design — no volume; the broker only carries learning events in transit (the consumer persists them in Postgres).

- Topics (auto-created): `unanswered-queries` (phase 1), `learned-answers` (phase 2)

```bash
cd kafka && docker compose up -d
```

## 3. `demo/` — the RAG application (main project)

Spring Boot service (`com.example.demo`, port 8080). Package layout:

| Package | Responsibility |
|---|---|
| `embedding/` | **Ingestion**: `PdfDocumentReader` (PDFBox), `DocumentChunker` (shared `splitText`: 1000 chars / 200 overlap), `BatchEmbeddingProcessor`, `VectorStorageService`, runnable `FullEmbeddingDemo` |
| `query/` | `VectorSearchService`, `SearchResult`, `QueryEmbeddingDemo` |
| `repository/` | `VectorRepository`: pgvector similarity search, learned-chunk insert + dedup |
| `service/` | `RagService`, `EmbeddingService` (query + document embeddings), `RerankerService`, `OllamaChatService`, `RagPromptBuilder`, `BookSearchTool`, `AgenticRagService`, `RagEvaluationService` |
| `learning/` | **Learning pipeline**: `UnansweredQueryConsumer` + `UnansweredQueryRepository` (queue), `UnansweredLearningService` (generate), `LearnedAnswerPublisher`, `LearnedAnswerConsumer` + `LearnedAnswerIngestionService` (embed + store) |
| `controller/` | `RagController`, `RagEvaluationController`, `LearningController` |
| `model/`, `exception/` | DTOs and events, validation errors (`GlobalExceptionHandler`) |
| `archived/` | Early learning prototypes; not part of the live flow |

Note: `llm/` holds an older copy of `RagApplication`, `RagPromptBuilder` and `OllamaChatService`; the live ones are in the root package and `service/`.

### API

| Endpoint | Use case |
|---|---|
| `POST /api/v1/rag/ask` | Standard RAG: embed, search, rerank, answer with sources. Body: `{"question": "..."}` |
| `POST /api/v1/rag/agent/ask` | Bounded **agentic RAG**: search, judge the evidence, optionally rewrite + search once more, then answer |
| `POST /api/v1/rag/evaluate?k=5` | Retrieval quality: Precision@K / Recall@K against `expectedChunkIds` |
| `POST /api/v1/rag/evaluate/compare?k=5` | Compare ranking before vs after reranking (e.g. MRR) |
| `GET /api/v1/learning/unanswered?status=PENDING&limit=50` | List recorded unanswered questions and their ids/status |
| `POST /api/v1/learning/unanswered/{id}/generate` | Generate an answer for one id with qwen3:8b, publish it, mark GENERATED |
| `POST /api/v1/learning/unanswered/process?limit=10` | Batch: process all PENDING (generate + publish each), skipping failures |

If no chunk reaches the similarity threshold, the RAG endpoints return a "no information" message instead of calling the model.

### Configuration (`src/main/resources/application.yaml`)

Retrieval: `rag.retrieval.top-k: 5`, `final-top-k: 3`, `similarity-threshold: 0.70`, `embedding-dimensions: 768`. Ollama at `localhost:11434` (`nomic-embed-text`, `qwen3:8b`); reranker at `localhost:8001`. Kafka at `localhost:9092`; learning: `topic: learned-answers`, `unanswered-topic: unanswered-queries`, `document-name: web-learned`, `dedup-threshold: 0.97`.

### Notes

[`demo/RAG_LEARNING_NOTES.md`](demo/RAG_LEARNING_NOTES.md) is a plain-language write-up of chunking, Top-K, precision/recall, thresholds, reranking, agentic RAG, plus glossary and a debugging checklist.

```bash
cd demo && mvn spring-boot:run
curl -X POST http://localhost:8080/api/v1/rag/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Explain consistent hashing"}'
```

## 4. `reranker-service/` — cross-encoder reranker

FastAPI app (`app.py`) loading `BAAI/bge-reranker-v2-m3` (max length 512).

- `POST /rerank` with `{"question": "...", "documents": ["...", "..."]}` returns `results` sorted by score: `documentIndex`, `score`, `content`
- Use case: improve the order of the vector-search candidates before the final top 3 go to the LLM. Scores are for sorting, not probabilities
- The `demo` app expects it on port **8001** (`app.py` does not set a port, so pass it explicitly)

```bash
cd reranker-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8001
```

## 5. `embedding-demo/` — local agent starters

### Python CLI agent (`agent.py`)
Standard-library-only terminal chat with Ollama (`qwen3:8b` by default; override with `OLLAMA_MODEL` / `OLLAMA_URL`). Tools: `list_files` and `read_file`, sandboxed to the folder, 40 KB read limit, max 6 tool rounds per turn. Commands: `/reset`, `/exit`.

```bash
cd embedding-demo && python3 agent.py
```

### `spring-agent/` — Spring AI agent service
Spring Boot WebFlux + Spring AI (`ollama-reactive-agent`, port 8082):
- `ProjectTools`: list/read files under `AGENT_PROJECT_ROOT` (defaults to the parent folder)
- `SystemDesignRagTool` → `SystemDesignRagService`: calls `demo`'s `POST /api/v1/rag/agent/ask`; when the book can't answer, records the question to Kafka (`unanswered-queries`) for later learning — no generation at request time
- `ChatController`: `POST /api/agent/chat` with `{"message": "..."}`, max 4,000 chars, returns the full answer as plain text
- Logs tool inputs/outputs at INFO; treat logs as sensitive with private data

```bash
cd embedding-demo/spring-agent && mvn spring-boot:run
curl http://localhost:8082/api/agent/chat -H 'Content-Type: application/json' \
  -d '{"message":"Explain consistent hashing in a payment system"}'
```

## 6. `documents/`

`system-design-book.pdf`, the knowledge source the RAG app indexes (about 441 chunks in the learning experiments).

---

## Self-learning pipeline (two-phase, async)

When a system-design question is **not** answered by the book, nothing is generated at request time. The question is recorded; later you call an API to generate an answer with the local model (qwen3:8b), which is then embedded and stored so the next identical question is served from pgvector. Learned text is chunked with the **same** `DocumentChunker.splitText` rule as the PDF ingestion.

```
Phase 1 (automatic):   agent: book can't answer ─► Kafka "unanswered-queries"
                       demo:  consume ─► INSERT unanswered_queries (PENDING)

Phase 2 (you trigger): POST /api/v1/learning/unanswered/{id}/generate
                       demo:  qwen3:8b answer ─► Kafka "learned-answers"
                       demo:  consume ─► chunk + embed + dedup + store ─► STORED
```

Status lifecycle: `PENDING → GENERATED → STORED`.

```bash
curl 'localhost:8080/api/v1/learning/unanswered?status=PENDING'       # find id
curl -X POST localhost:8080/api/v1/learning/unanswered/1/generate      # learn one
curl -X POST 'localhost:8080/api/v1/learning/unanswered/process?limit=10'  # learn all pending
```

Fully offline. Full design, status lifecycle, topics, API and a walkthrough: [docs/learning-pipeline.md](docs/learning-pipeline.md).

---

## Run everything with one script

`run-all.sh` starts, stops and monitors the whole stack (Ollama, Postgres, Kafka, reranker, demo, spring-agent):

```bash
./run-all.sh start              # start all, in dependency order, waiting for each port
./run-all.sh start demo agent   # start only some services (aliases: spring-agent, kafka, …)
./run-all.sh status             # which ports are up
./run-all.sh logs demo          # tail a log (logs and PID files live in .run/)
./run-all.sh stop               # stop all (Postgres data volume is kept)
./run-all.sh restart kafka
```

It reuses anything already running on the same port, creates the reranker `.venv` on first run, and pulls missing Ollama models. It does not ingest the PDF or load the schema — do those once (below).

## Run order for the full stack

1. Start Ollama and pull models: `ollama pull nomic-embed-text && ollama pull qwen3:8b`
2. `postgres/`: `docker compose up -d`, then load the schema once: `docker exec -i rag-postgres psql -U raguser -d ragdb < postgres/schema.sql`
3. `kafka/`: `docker compose up -d`
4. `demo/`: run the ingestion once (`FullEmbeddingDemo`) to fill `document_chunks`, then start the app on 8080
5. `reranker-service/`: start on port 8001
6. `embedding-demo/spring-agent/`: start on 8082 for the chat agent

Or just `./run-all.sh start` after step 1 and the one-time schema load + ingestion.

## Prerequisites

Docker, Java 17+, Maven 3.9+, Python 3.10+, and Ollama. Everything runs locally; no external API keys are required.
