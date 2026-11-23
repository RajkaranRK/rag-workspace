#!/usr/bin/env python3
"""A small, local tool-using agent powered by Ollama."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parent
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
MODEL = os.getenv("OLLAMA_MODEL", "qwen3:4b")
MAX_FILE_BYTES = 40_000

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "list_files",
            "description": "List files in the project directory (optionally within a relative subdirectory).",
            "parameters": {
                "type": "object",
                "properties": {"directory": {"type": "string", "description": "Relative directory; defaults to the project root."}},
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "read_file",
            "description": "Read a UTF-8 text file inside the project directory. Use a relative path.",
            "parameters": {
                "type": "object",
                "properties": {"path": {"type": "string", "description": "Relative path to a project text file."}},
                "required": ["path"],
            },
        },
    },
]


def project_path(relative: str) -> Path:
    candidate = (ROOT / relative).resolve()
    if candidate != ROOT and ROOT not in candidate.parents:
        raise ValueError("That path is outside the project directory.")
    return candidate


def run_tool(name: str, arguments: dict) -> str:
    try:
        if name == "list_files":
            directory = project_path(arguments.get("directory", "."))
            if not directory.is_dir():
                return "That directory does not exist."
            files = sorted(p.relative_to(ROOT).as_posix() for p in directory.rglob("*") if p.is_file() and ".git" not in p.parts)
            return "\n".join(files[:200]) or "No files found."

        if name == "read_file":
            path = project_path(arguments["path"])
            if not path.is_file():
                return "That file does not exist."
            if path.stat().st_size > MAX_FILE_BYTES:
                return f"File is too large to read (limit: {MAX_FILE_BYTES} bytes)."
            return path.read_text(encoding="utf-8")

        return f"Unknown tool: {name}"
    except (KeyError, OSError, UnicodeError, ValueError) as exc:
        return f"Could not run tool: {exc}"


def ollama_chat(messages: list[dict]) -> dict:
    body = json.dumps({"model": MODEL, "messages": messages, "tools": TOOLS, "stream": False}).encode()
    request = urllib.request.Request(
        f"{OLLAMA_URL.rstrip('/')}/api/chat",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Cannot reach Ollama at {OLLAMA_URL}. Start Ollama and try again. ({exc})") from exc


def answer(user_text: str, history: list[dict]) -> str:
    history.append({"role": "user", "content": user_text})
    for _ in range(6):
        result = ollama_chat(history)
        message = result.get("message", {})
        history.append(message)
        calls = message.get("tool_calls", [])
        if not calls:
            return message.get("content", "").strip()
        for call in calls:
            function = call.get("function", {})
            output = run_tool(function.get("name", ""), function.get("arguments", {}))
            history.append({"role": "tool", "content": output})
    return "I reached the tool-call limit for this turn. Please try a more focused request."


def main() -> None:
    print(f"Local Ollama agent · model: {MODEL}")
    print("Ask about files in this project. Type /exit to quit; /reset clears chat history.\n")
    history = [{"role": "system", "content": "You are a helpful assistant running locally. Use project file tools when useful. Only read files within the project directory. Be clear when the project files do not contain the answer."}]
    while True:
        try:
            prompt = input("You: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nGoodbye.")
            break
        if not prompt:
            continue
        if prompt.lower() in {"/exit", "/quit"}:
            print("Goodbye.")
            break
        if prompt.lower() == "/reset":
            history = history[:1]
            print("Chat history cleared.\n")
            continue
        try:
            response = answer(prompt, history)
            print(f"Agent: {response}\n")
        except (RuntimeError, json.JSONDecodeError, KeyError) as exc:
            print(f"Error: {exc}\n")


if __name__ == "__main__":
    main()
