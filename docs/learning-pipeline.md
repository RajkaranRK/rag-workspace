# Self-learning pipeline (two-phase, async)

When the agent classifies a question as system design and the book cannot answer
it, the system does **not** generate anything at request time. It records the
question, and later — on an explicit API call — generates an answer with the
local model, embeds it, and stores it in the vector database so the next time
the question is asked it is served from the book/vector store.

## Flow

```
PHASE 1 — record (automatic, at query time)
  user ─► spring-agent /api/agent/chat ─► systemDesignRag tool
            └─► POST demo /api/v1/rag/agent/ask         (ask the book)
                 │ sufficient ─► return book answer
                 ▼ insufficient
            UnansweredQueryPublisher ─► Kafka "unanswered-queries"
                                          │  (JSON: eventId, question, createdAt)
            demo UnansweredQueryConsumer ◄┘
                 └─► INSERT unanswered_queries (status=PENDING)
            (agent returns "queued for learning"; no LLM work here)

PHASE 2 — generate + store (on demand, you trigger it)
  you ─► GET  demo /api/v1/learning/unanswered?status=PENDING      (find the id)
  you ─► POST demo /api/v1/learning/unanswered/{id}/generate
            └─► OllamaChatService (qwen3:8b) generates the answer
                 └─► LearnedAnswerPublisher ─► Kafka "learned-answers"
                      │  (JSON: question, answer, sourceId, ...)
                      └─► mark row GENERATED
            demo LearnedAnswerConsumer ◄┘
                 └─► chunk + embed (nomic) + dedup ─► INSERT document_chunks
                      └─► mark row STORED
  ▼
  Next time the question is asked, phase-1's book lookup finds it → no new work.
```

Producer of phase 1 = `spring-agent`. Everything else (both consumers, the table,
the generate API, the LLM call, embedding and storage) = `demo`, which already
owns the DB and embeddings.

## Status lifecycle (`unanswered_queries`)

`PENDING` (recorded) → `GENERATED` (LLM answer produced + published) → `STORED`
(embedded into `document_chunks`).

## API

| Method & path | Purpose |
|---|---|
| `GET /api/v1/learning/unanswered?status=PENDING&limit=50` | List recorded questions and their ids/status |
| `POST /api/v1/learning/unanswered/{id}/generate` | Generate an answer for that id with qwen3:8b, publish it, mark GENERATED |
| `POST /api/v1/learning/unanswered/process?limit=10` | Process all PENDING (generate+publish each); skips failures |

```bash
# 1) ask something the book doesn't cover (gets queued)
curl localhost:8082/api/agent/chat -H 'Content-Type: application/json' \
  -d '{"message":"why is redis so fast"}'

# 2) find the recorded question's id
curl 'localhost:8080/api/v1/learning/unanswered?status=PENDING'

# 3) generate + learn it (use the id from step 2)
curl -X POST localhost:8080/api/v1/learning/unanswered/1/generate

# 4) it is now embedded; ask again and the book answers it
curl localhost:8080/api/v1/rag/ask -H 'Content-Type: application/json' \
  -d '{"question":"why is redis so fast"}'
```

## Topics

| Topic | Producer | Consumer | Payload |
|---|---|---|---|
| `unanswered-queries` | spring-agent | demo (`UnansweredQueryConsumer`) | eventId, question, createdAt |
| `learned-answers` | demo (generate API) | demo (`LearnedAnswerConsumer`) | question, answer, sourceId, ... |

## Configuration

| Key (env) | Default | Meaning |
|---|---|---|
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka brokers (both apps) |
| `LEARNING_UNANSWERED_TOPIC` / `learning.unanswered-topic` | `unanswered-queries` | Phase-1 topic |
| `LEARNING_TOPIC` / `learning.topic` | `learned-answers` | Phase-2 topic |
| `learning.document-name` | `web-learned` | `document_name` for stored learned rows |
| `learning.dedup-threshold` | `0.97` | Skip insert if an existing vector is at least this similar |

## Why this shape

- **No work at request time** beyond recording — the chat stays fast and the
  expensive LLM generation is deferred and explicitly triggered.
- **JSON strings on topics** — the two apps have different packages; a string
  contract avoids deserializer/type coupling.
- **Idempotent recording** — a question with an active (PENDING/GENERATED) row is
  not re-queued; dedup on embedding insert avoids storing the same answer twice.
- **Learned rows are marked** `document_name='web-learned'` with negative chunk
  numbers so they never collide with the positive book chunk IDs used by the
  evaluation endpoints.
- **Caveat:** stored answers are the model's own output. Future retrieval serves
  them as source material, including any mistakes. The generate step is where a
  human/API decides which questions become knowledge, which is the review point.

## Observability

Phase 1 (agent): `Pipeline start qid=...` → `decision=QUEUED_FOR_LEARNING`.
Phase 1 (demo): `Unanswered query eventId=... recorded=true`.
Phase 2 (demo): `Generating answer for unanswered query id=...` →
`Published learned answer eventId=... sourceId=...` → `Learned answer stored` →
`Marked unanswered query id=... as STORED`.
