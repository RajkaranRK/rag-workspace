# Reactive Spring AI agent with Ollama

This Java version mirrors the Python starter: Ollama is the brain, Spring AI connects to the model, and `ProjectTools` exposes `listFiles` and `readFile`. Spring WebFlux serves the chat API and returns the complete answer after generation finishes.

## Requirements

- Java 17 or later
- Maven 3.9 or later
- Ollama installed and running
- A model that supports tool calling, such as Qwen 3

Pull the model once:

```bash
ollama pull qwen3:8b
```

Ollama tool calling requires Ollama 0.2.8 or later; streaming tool calls require Ollama 0.4.6 or later.

## Run

From this `spring-agent` directory:

```bash
mvn spring-boot:run
```

The server listens on port `8082` by default, uses Ollama at `http://localhost:11434`, and selects model `qwen3:8b`. Override them with environment variables:

```bash
PORT=8082 OLLAMA_URL=http://localhost:11434 OLLAMA_MODEL=qwen3:8b mvn spring-boot:run
```

The tools use the parent workspace as their project root by default. Set `AGENT_PROJECT_ROOT` to point to another directory.

## Ask a question

The endpoint returns one accumulated plain-text answer:

```bash
curl http://localhost:8082/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"List the project files and summarize the RAG notes."}'
```

The request body must contain a non-blank `message` of at most 4,000 characters.

## Tool execution logs

The application logs chat request start/completion/failure and each tool invocation. Tool logs include the selected tool name, its input parameters, execution time, and returned output (or error). These logs use Spring Boot's default INFO level and appear in the console. Since tool inputs and outputs may contain user questions or document text, treat the console logs as sensitive and adjust the log statements before using this with private data.

## How the pieces fit

```text
HTTP client
  → WebFlux ChatController
  → Spring AI ChatClient
  → Ollama model
  → ProjectTools when the model requests a tool
  → complete response returned to the client
```

`ProjectTools` only reads files under the configured project root. It does not modify files. This starter is stateless between HTTP requests; conversation memory can be added next.

## Connect the system design RAG API

The `SystemDesignRagTool` calls the existing RAG service when the model identifies a system design or architecture question. The tool sends the full question as JSON:

```json
{"question":"How does consistent hashing distribute keys and how can we use it in a payment system?"}
```

By default it posts to `http://localhost:8080/api/v1/rag/agent/ask`. Change the target with `RAG_API_URL` if needed:

```bash
RAG_API_URL=http://localhost:8080/api/v1/rag/agent/ask mvn spring-boot:run
```

The agent service uses port 8082 by default so the existing RAG app can keep port 8080. The RAG app must be running for this tool to return retrieved context. The model is instructed to call this tool before answering system design questions.

## Reactive behavior

The controller collects generated text chunks into a `Mono<String>`, so the client receives the answer only after generation finishes. Spring AI tool execution is synchronous in this starter: the file reads are short, while the RAG HTTP tool waits up to 90 seconds for the existing API. Tool calls require an Ollama model that supports tool calling. The server-to-server call uses WebClient, but Spring AI's tool callback contract means the callback waits for its response before returning it to the model.
