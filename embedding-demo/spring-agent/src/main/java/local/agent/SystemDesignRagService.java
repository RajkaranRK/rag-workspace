package local.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Orchestrates the system-design answer path:
 *   1. Ask the existing RAG app (vector store over the book).
 *   2. If the book answers, return it.
 *   3. If not, do NOT generate anything at request time. Record the unanswered
 *      question on Kafka (phase 1). It is later turned into a stored answer by
 *      the RAG app's generate API (phase 2): LLM answer -> Kafka -> embed -> store.
 *
 * Every log line carries qid=<id> (also the unanswered-query eventId).
 */
@Service
public class SystemDesignRagService {

    private static final Logger log = LoggerFactory.getLogger(SystemDesignRagService.class);
    private static final Duration RAG_TIMEOUT = Duration.ofSeconds(90);

    private final WebClient webClient;
    private final String ragApiUrl;
    private final boolean validateRagCompleteness;
    private final AnswerValidator answerValidator;
    private final UnansweredQueryPublisher unansweredPublisher;
    private final ObjectMapper objectMapper;

    public SystemDesignRagService(
            WebClient.Builder webClientBuilder,
            @Value("${agent.rag-api-url}") String ragApiUrl,
            @Value("${web-search.validate-rag-completeness:true}") boolean validateRagCompleteness,
            AnswerValidator answerValidator,
            UnansweredQueryPublisher unansweredPublisher,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.build();
        this.ragApiUrl = ragApiUrl;
        this.validateRagCompleteness = validateRagCompleteness;
        this.answerValidator = answerValidator;
        this.unansweredPublisher = unansweredPublisher;
        this.objectMapper = objectMapper;
        log.info("SystemDesignRagService ready ragApiUrl={} validateRagCompleteness={}",
                ragApiUrl, validateRagCompleteness);
    }

    /** Returns the tool output string handed back to the agent/LLM. */
    public String answer(String question) {
        String qid = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        log.info("Pipeline start qid={} question='{}'", qid, question);

        String ragJson = callRag(qid, question);

        JsonNode rag = tryParse(ragJson);
        if (rag == null) {
            log.warn("qid={} RAG returned a non-JSON/error response; returning it unchanged", qid);
            return ragJson;
        }

        if (isRagSufficient(qid, question, rag)) {
            log.info("qid={} decision=SERVE_FROM_BOOK totalMs={}", qid, elapsedMs(startedAt));
            return ragJson;
        }

        // Phase 1: record the unanswered question for later learning. No generation here.
        unansweredPublisher.publish(
                new UnansweredQueryEvent(qid, question, Instant.now().toString()));
        log.info("qid={} decision=QUEUED_FOR_LEARNING totalMs={}", qid, elapsedMs(startedAt));
        return buildQueuedJson(qid);
    }

    private String callRag(String qid, String question) {
        long startedAt = System.nanoTime();
        log.info("qid={} calling RAG {}", qid, ragApiUrl);
        try {
            String result = webClient.post()
                    .uri(ragApiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new RagQuestion(question))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(RAG_TIMEOUT);
            String output = (result == null || result.isBlank())
                    ? "The RAG service returned an empty response." : result;
            log.info("qid={} RAG call ok durationMs={}", qid, elapsedMs(startedAt));
            return output;
        } catch (WebClientResponseException e) {
            log.warn("qid={} RAG call failed httpStatus={} durationMs={} body={}",
                    qid, e.getStatusCode().value(), elapsedMs(startedAt), e.getResponseBodyAsString());
            return "RAG service returned HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString();
        } catch (RuntimeException e) {
            log.warn("qid={} RAG call could not reach {} durationMs={}: {}",
                    qid, ragApiUrl, elapsedMs(startedAt), e.getMessage());
            return "Could not reach the RAG service at " + ragApiUrl + ". Check that it is running. Details: "
                    + e.getMessage();
        }
    }

    private boolean isRagSufficient(String qid, String question, JsonNode rag) {
        JsonNode sources = rag.path("sources");
        boolean hasSources = sources.isArray() && sources.size() > 0;
        String answer = rag.path("answer").asText("");
        boolean notFound = looksLikeNotFound(answer);

        if (!hasSources || answer.isBlank() || notFound) {
            log.info("qid={} RAG sufficiency=false hasSources={} blank={} looksLikeNotFound={}",
                    qid, hasSources, answer.isBlank(), notFound);
            return false;
        }
        if (validateRagCompleteness) {
            boolean complete = answerValidator.isRagAnswerComplete(question, answer);
            log.info("qid={} RAG sufficiency={} completenessJudge={} sources={}",
                    qid, complete, complete, sources.size());
            return complete;
        }
        log.info("qid={} RAG sufficiency=true sources={} completenessJudge=skipped", qid, sources.size());
        return true;
    }

    private boolean looksLikeNotFound(String answer) {
        String a = answer.toLowerCase();
        return a.contains("could not find")
                || a.contains("not enough information")
                || a.contains("do not provide enough information")
                || a.contains("does not contain")
                || a.contains("no relevant information");
    }

    private String buildQueuedJson(String qid) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("answer", "This is not in the book yet. The question has been recorded for learning; "
                + "an answer will be generated and added to the knowledge base on request.");
        node.put("origin", "queued-for-learning");
        node.put("queued", true);
        node.put("eventId", qid);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"answer\":\"This question has been recorded for learning.\",\"queued\":true}";
        }
    }

    private JsonNode tryParse(String s) {
        try {
            JsonNode n = objectMapper.readTree(s);
            return n.isObject() ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private record RagQuestion(String question) {}
}
