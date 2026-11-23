package local.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent tool for system-design / architecture questions. It delegates to
 * {@link SystemDesignRagService}, which queries the RAG app and, when the book
 * does not answer, falls back to a validated web search that is also learned
 * into the vector store.
 */
@Component
public class SystemDesignRagTool {

    private static final Logger log = LoggerFactory.getLogger(SystemDesignRagTool.class);

    private final SystemDesignRagService service;

    public SystemDesignRagTool(SystemDesignRagService service) {
        this.service = service;
    }

    @Tool(description = "Search the existing RAG application for a system design or architecture question. "
            + "Call this for system design questions, including questions that apply design concepts to a domain.")
    public String systemDesignRag(
            @ToolParam(description = "The user's complete system design question") String question) {
        long startedAt = System.nanoTime();
        log.info("Tool invoked tool=systemDesignRag question={}", question);
        String output = service.answer(question);
        log.info("Tool completed tool=systemDesignRag durationMs={} output={}",
                (System.nanoTime() - startedAt) / 1_000_000, output);
        return output;
    }
}
