# rag-workspace

A local, fully offline **Retrieval-Augmented Generation (RAG)** workspace. It lets you ask questions about a system-design book (`documents/system-design-book.pdf`) and get grounded answers with sources, using only models running on your own machine.

## Projects at a glance

| Folder | Tech | Port | Use case |
|---|---|---|---|
| [`postgres/`](postgres) | Docker Compose, PostgreSQL 16 + pgvector | 5432 | Vector database that stores book chunks and their embeddings |
| [`demo/`](demo) | Java 17, Spring Boot 3.2, PDFBox, JDBC | 8080 | **The core RAG application**: ingestion, vector search, reranking, answer generation, evaluation, bounded agentic RAG |
| [`reranker-service/`](reranker-service) | Python, FastAPI, sentence-transformers | 8001 (set at start) | Second-stage reranker that reorders search results with `BAAI/bge-reranker-v2-m3` |
| [`embedding-demo/`](embedding-demo) | Python (stdlib), Ollama | n/a (CLI) | Starter local tool-using agent in the terminal (list/read project files) |
| [`embedding-demo/spring-agent/`](embedding-demo/spring-agent) | Java 17, Spring Boot 3.5, Spring AI 1.1, WebFlux | 8082 | HTTP chat agent that can read project files and call the RAG API as a tool |
| [`documents/`](documents) | PDF | n/a | Source material (the system design book, ~23 MB) |

## How the pieces fit together

```
                      documents/system-design-book.pdf
                                   │  (ingestion, one time)
                                   ▼
   demo (PdfDocumentReader → DocumentChunker → BatchEmbeddingProcessor)
                                   │  embeddings via Ollama (nomic-embed-text)
                                   ▼
                      postgres (pgvector, table of chunks)
                                   ▲
                                   │  vector search (top-k = 5, cosine, threshold 0.70)
 question ─► demo /api/v1/rag/ask ─┤
                                   ▼
                 reranker-service /rerank  (keep final top 3)
                                   ▼
                 Ollama qwen3:4b writes answer + sources

 question ─► spring-agent :8082 ─► (Qwen decides) ─► demo /api/v1/rag/agent/ask
                                              └──► ProjectTools (list/read local files)
```

External requirement for everything: **[Ollama](https://ollama.com)** running on `localhost:11434` with `nomic-embed-text` (768-dim embeddings) and `qwen3:4b` (answers and agent tool calling).

---

## 1. `postgres/` — vector store

`docker-compose.yml` starts `pgvector/pgvector:pg16` as container `rag-postgres`.

- Database `ragdb`, user `raguser`, password `ragpassword` (local development defaults), port `5432`
- Persistent volume `postgres_data`, health check via `pg_isready`
- Use case: holds the chunk text, page numbers, and 768-dimension embeddings searched by the `demo` app (`embedding <=> query` cosine distance)

```bash
cd postgres && docker compose up -d
```

## 2. `demo/` — the RAG application (main project)

Spring Boot service (`com.example.demo`, port 8080). Package layout:

| Package | Responsibility |
|---|---|
| `embedding/` | **Ingestion pipeline**: `PdfDocumentReader` (PDFBox), `DocumentChunker` (~1000 chars, 200 overlap), `BatchEmbeddingProcessor`, `VectorStorageService`, runnable `FullEmbeddingDemo` |
| `query/` | `VectorSearchService`, `SearchResult`, `QueryEmbeddingDemo` |
| `repository/` | `VectorRepository`: pgvector SQL similarity search |
| `service/` | `RagService` (orchestrates the flow), `EmbeddingService`, `RerankerService`, `OllamaChatService`, `RagPromptBuilder`, `BookSearchTool`, `AgenticRagService`, `RagEvaluationService` |
| `controller/` | `RagController`, `RagEvaluationController` |
| `model/`, `exception/` | Request/response DTOs, validation errors (`GlobalExceptionHandler`) |
| `archived/` | Early learning prototypes (text chunker, PDF reader, embedding demos); not part of the live flow |

Note: `llm/` holds an older copy of `RagApplication`, `RagPromptBuilder` and `OllamaChatService`; the live ones are in the root package and `service/`.

### API (base path `/api/v1/rag`)

| Endpoint | Use case |
|---|---|
| `POST /ask` | Standard RAG: embed question, vector search, rerank, answer with sources. Body: `{"question": "..."}` |
| `POST /agent/ask` | Bounded **agentic RAG**: search, let the model judge the evidence, optionally rewrite the query and search once more, then answer. Returns the queries tried |
| `POST /evaluate?k=5` | Retrieval quality check: Precision@K / Recall@K against `expectedChunkIds` |
| `POST /evaluate/compare?k=5` | Compare ranking before vs after reranking (e.g. MRR) |

If no chunk reaches the similarity threshold, the app returns a "no information" message instead of calling the model.

### Configuration (`src/main/resources/application.yaml`)

`rag.retrieval.top-k: 5`, `final-top-k: 3`, `similarity-threshold: 0.70`, `embedding-dimensions: 768`; Ollama at `localhost:11434`; reranker at `localhost:8001`.

### Notes

[`demo/RAG_LEARNING_NOTES.md`](demo/RAG_LEARNING_NOTES.md) is a plain-language write-up of what was learned: chunking, Top-K, precision/recall, thresholds, reranking, agentic RAG, plus glossary and a debugging checklist.

```bash
cd demo && mvn spring-boot:run
curl -X POST http://localhost:8080/api/v1/rag/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Explain consistent hashing"}'
```

## 3. `reranker-service/` — cross-encoder reranker

FastAPI app (`app.py`) loading `BAAI/bge-reranker-v2-m3` (max length 512).

- `POST /rerank` with `{"question": "...", "documents": ["...", "..."]}` returns `results` sorted by score: `documentIndex`, `score`, `content`
- Use case: improve the order of the vector-search candidates before the top 3 go to the LLM. Scores are for sorting, not percentages
- The `demo` app expects it on port **8001** (`app.py` does not set a port, so pass it explicitly)

```bash
cd reranker-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8001
```

## 4. `embedding-demo/` — local agent starters

### Python CLI agent (`agent.py`)
Standard-library-only terminal chat with Ollama (`qwen3:4b` by default; override with `OLLAMA_MODEL` / `OLLAMA_URL`). Tools: `list_files` and `read_file`, sandboxed to the folder, 40 KB read limit, max 6 tool rounds per turn. Commands: `/reset`, `/exit`. Use case: learning how tool-calling agents work with no dependencies.

```bash
cd embedding-demo && python3 agent.py
```

### `spring-agent/` — Spring AI agent service
Spring Boot WebFlux + Spring AI (`ollama-reactive-agent`, port 8082). Java version of the above, extended:
- `ProjectTools`: list/read files under `AGENT_PROJECT_ROOT` (defaults to the parent folder)
- `SystemDesignRagTool`: calls `demo`'s `POST /api/v1/rag/agent/ask` (`RAG_API_URL`) for system-design questions
- `ChatController`: `POST /api/agent/chat` with `{"message": "..."}`, max 4,000 chars, returns the full answer as plain text
- Logs tool inputs and outputs at INFO; treat logs as sensitive with private data

```bash
cd embedding-demo/spring-agent && mvn spring-boot:run
curl http://localhost:8082/api/agent/chat -H 'Content-Type: application/json' \
  -d '{"message":"Explain consistent hashing in a payment system"}'
```

## 5. `documents/`

`system-design-book.pdf`, the knowledge source the RAG app indexes (about 441 chunks in the learning experiments).

---

## Run order for the full stack

1. Start Ollama and pull models: `ollama pull nomic-embed-text && ollama pull qwen3:4b`
2. `postgres/`: `docker compose up -d`
3. `demo/`: run the ingestion once (`FullEmbeddingDemo`) to fill the database, then start the app on 8080
4. `reranker-service/`: start on port 8001
5. Optional: start `embedding-demo/spring-agent` on 8082 for the chat agent

Check the ingestion class and database table setup in `demo/` before step 3; the repo contains no SQL schema file, so the table was presumably created by hand.

## Prerequisites

Docker, Java 17+, Maven 3.9+, Python 3.10+, Ollama.
