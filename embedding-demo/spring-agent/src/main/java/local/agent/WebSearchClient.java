package local.agent;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Pluggable web search. Two providers:
 *
 *  - "wikipedia" (default): keyless, free, no signup. Good for conceptual
 *    system-design questions. Uses the MediaWiki API.
 *  - "tavily": full-web search, needs TAVILY_API_KEY.
 *
 * Disabled (no-op) only when turned off, or when provider=tavily and no key is
 * set, so the rest of the agent keeps working.
 */
@Component
public class WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(WebSearchClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT =
            "rag-workspace-agent/1.0 (local learning project)";
    private static final int MAX_CHARS_PER_SOURCE = 1500;

    private final WebClient webClient;
    private final boolean enabled;
    private final String provider;
    private final String apiKey;
    private final String tavilyUrl;
    private final String wikipediaUrl;
    private final int maxResults;

    public WebSearchClient(
            WebClient.Builder webClientBuilder,
            @Value("${web-search.enabled:true}") boolean enabled,
            @Value("${web-search.provider:wikipedia}") String provider,
            @Value("${web-search.api-key:}") String apiKey,
            @Value("${web-search.tavily-url:https://api.tavily.com/search}") String tavilyUrl,
            @Value("${web-search.wikipedia-url:https://en.wikipedia.org/w/api.php}") String wikipediaUrl,
            @Value("${web-search.max-results:2}") int maxResults
    ) {
        this.webClient = webClientBuilder.build();
        this.provider = provider == null ? "wikipedia" : provider.trim().toLowerCase();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.tavilyUrl = tavilyUrl;
        this.wikipediaUrl = wikipediaUrl;
        this.maxResults = maxResults;

        boolean needsKey = this.provider.equals("tavily");
        this.enabled = enabled && (!needsKey || !this.apiKey.isBlank());

        if (!this.enabled) {
            if (needsKey && this.apiKey.isBlank()) {
                log.warn("web-search provider=tavily but no api-key set; web fallback is OFF. "
                        + "Set TAVILY_API_KEY, or use the keyless default (web-search.provider=wikipedia).");
            } else {
                log.warn("web-search is OFF (web-search.enabled=false)");
            }
        } else {
            log.info("web-search ON provider={} maxResults={}", this.provider, maxResults);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public WebSearchResult search(String query) {
        if (!enabled) {
            return new WebSearchResult(null, List.of());
        }
        try {
            return switch (provider) {
                case "tavily" -> tavilySearch(query);
                case "wikipedia" -> wikipediaSearch(query);
                default -> {
                    log.warn("unknown web-search provider '{}'; no results", provider);
                    yield new WebSearchResult(null, List.of());
                }
            };
        } catch (RuntimeException e) {
            log.warn("web search failed provider={} query='{}': {}", provider, query, e.getMessage());
            return new WebSearchResult(null, List.of());
        }
    }

    private WebSearchResult tavilySearch(String query) {
        Map<String, Object> body = Map.of(
                "api_key", apiKey,
                "query", query,
                "search_depth", "advanced",
                "include_answer", true,
                "max_results", maxResults);

        JsonNode json = webClient.post()
                .uri(tavilyUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(TIMEOUT);

        if (json == null) {
            return new WebSearchResult(null, List.of());
        }
        String answer = json.path("answer").asText(null);
        List<WebSearchResult.Source> sources = new ArrayList<>();
        for (JsonNode r : json.path("results")) {
            sources.add(new WebSearchResult.Source(
                    r.path("url").asText(""),
                    r.path("content").asText("")));
        }
        log.info("web search provider=tavily query='{}' hasAnswer={} results={}",
                query, answer != null && !answer.isBlank(), sources.size());
        return new WebSearchResult(answer, sources);
    }

    private WebSearchResult wikipediaSearch(String query) {
        // One call: search for matching pages and pull their plain-text intros.
        // exlimit=max is essential: the extracts API defaults to exlimit=1, which
        // returns a body for only ONE page regardless of how many the search
        // matched. A cleaned, keyword-focused query ranks the right article first.
        String searchQuery = toSearchQuery(query);
        String url = wikipediaUrl
                + "?action=query&format=json&prop=extracts&explaintext=1&exintro=1"
                + "&exlimit=max&redirects=1"
                + "&generator=search&gsrlimit=" + maxResults
                + "&gsrsearch=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
        log.info("wikipedia search rawQuery='{}' searchQuery='{}'", query, searchQuery);

        JsonNode json = webClient.get()
                .uri(URI.create(url))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(TIMEOUT);

        List<WebSearchResult.Source> sources = new ArrayList<>();
        if (json != null) {
            JsonNode pages = json.path("query").path("pages");
            for (JsonNode page : pages) {
                String title = page.path("title").asText("");
                String extract = page.path("extract").asText("");
                if (extract.isBlank()) {
                    continue;
                }
                if (extract.length() > MAX_CHARS_PER_SOURCE) {
                    extract = extract.substring(0, MAX_CHARS_PER_SOURCE);
                }
                long pageId = page.path("pageid").asLong(0);
                String pageUrl = pageId > 0
                        ? "https://en.wikipedia.org/?curid=" + pageId
                        : "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
                sources.add(new WebSearchResult.Source(pageUrl, title + "\n" + extract));
            }
        }
        log.info("web search provider=wikipedia query='{}' results={}", query, sources.size());
        return new WebSearchResult(null, sources);
    }

    /**
     * Turn a natural-language question into a keyword query that Wikipedia's
     * full-text search ranks well. Strips common question/stop words; falls back
     * to the original if stripping leaves nothing.
     */
    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "do", "does", "did", "of", "to", "in", "on", "for", "and", "or",
            "why", "how", "what", "when", "where", "who", "which", "whose",
            "so", "very", "really", "much", "that", "this", "it", "its",
            "can", "could", "would", "should", "will", "about", "as", "at");

    static String toSearchQuery(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String[] words = question.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank() && !STOP_WORDS.contains(w)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(w);
            }
        }
        String cleaned = sb.toString().trim();
        return cleaned.isBlank() ? question.trim() : cleaned;
    }
}