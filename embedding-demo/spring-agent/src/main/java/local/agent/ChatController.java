package local.agent;

import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agent")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String SYSTEM_PROMPT = "You are a helpful local assistant. Use project file tools when useful. "
            + "For questions about system design, architecture, or designing a system, always call the "
            + "systemDesignRag tool and base your answer on its response. Do not answer system design questions "
            + "from memory without first calling that tool. Only use project file tools within the project directory. "
            + "Say clearly when the files or RAG response do not contain the answer.";
    private final ChatClient chatClient;
    private final ProjectTools projectTools;
    private final SystemDesignRagTool systemDesignRagTool;

    public ChatController(ChatClient chatClient, ProjectTools projectTools, SystemDesignRagTool systemDesignRagTool) {
        this.chatClient = chatClient;
        this.projectTools = projectTools;
        this.systemDesignRagTool = systemDesignRagTool;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> chat(@Valid @RequestBody ChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("Chat request started requestId={} messageLength={}", requestId, request.message().length());
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(request.message())
                .tools(projectTools, systemDesignRagTool)
                .stream()
                .content()
                .collect(Collectors.joining())
                .doOnSuccess(response -> log.info("Chat request completed requestId={} responseLength={}",
                        requestId, response == null ? 0 : response.length()))
                .doOnError(error -> log.error("Chat request failed requestId={}", requestId, error));
    }

    public record ChatRequest(@NotBlank @Size(max = 4000) String message) {}
}
