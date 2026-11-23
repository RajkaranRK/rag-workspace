# Local Python agent with Ollama

This is a small command-line agent that runs on your machine and talks to an Ollama model. It can answer questions, list project files, and read text files in this folder. It uses Python's standard library, so there are no Python packages to install.

## Java Spring Boot version

The reactive Spring AI version is in [spring-agent/README.md](spring-agent/README.md). It exposes a streaming HTTP endpoint and the same project-file tools.

## 1. Install Ollama and download a model

Install Ollama from [ollama.com/download](https://ollama.com/download), then open a terminal and run:

```bash
ollama pull qwen3:4b
```

Ollama normally starts its local service automatically. If it is not running, start it with:

```bash
ollama serve
```

## 2. Start the agent

From this project folder, run:

```bash
python3 agent.py
```

Try asking `What files are in this project?` or `Summarize the RAG notes.` Type `/reset` to clear conversation history and `/exit` to quit.

## Choose another model or Ollama address

The defaults are model `qwen3:4b` and address `http://localhost:11434`. Override them for one run like this:

```bash
OLLAMA_MODEL=llama3.2:3b python3 agent.py
OLLAMA_URL=http://localhost:11434 python3 agent.py
```

The model must be available locally (`ollama pull MODEL_NAME`). This starter agent reads project text files only; it does not yet index PDFs, use a vector database, or modify files.
