package com.example.demo.service;

import com.example.demo.model.AskResponse;
import com.example.demo.query.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final BookSearchTool bookSearchTool;
    private final RagPromptBuilder promptBuilder;
    private final OllamaChatService chatService;

    public RagService(
            BookSearchTool bookSearchTool,
            RagPromptBuilder promptBuilder,
            OllamaChatService chatService
    ) {
        this.bookSearchTool = bookSearchTool;
        this.promptBuilder = promptBuilder;
        this.chatService = chatService;
    }

    public AskResponse ask(String question) throws Exception {

        // 1. Search for passages using the same retrieval steps as before.
        List<SearchResult> finalContext =
                bookSearchTool.search(question);

        if (finalContext.isEmpty()) {
            return fallbackResponse();
        }

        // 2. Build prompt
        String prompt =
                promptBuilder.buildPrompt(question, finalContext);

        // 3. Generate answer
        String answer =
                chatService.generateAnswer(prompt);

        // 4. Prepare source references
        List<AskResponse.Source> sources = finalContext.stream()
                .map(result -> new AskResponse.Source(
                        result.documentName(),
                        result.pageNumber(),
                        result.chunkNumber(),
                        result.cosineSimilarity()
                ))
                .toList();

        return new AskResponse(answer, sources);
    }

    private AskResponse fallbackResponse() {
        return new AskResponse(
                "I could not find relevant information in the book.",
                List.of()
        );
    }
}
