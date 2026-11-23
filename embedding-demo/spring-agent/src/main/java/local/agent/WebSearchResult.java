package local.agent;

import java.util.List;

/** Normalised result from a web search provider. */
public record WebSearchResult(
        String directAnswer,
        List<Source> sources
) {
    public record Source(String url, String content) {}

    public boolean isEmpty() {
        return (directAnswer == null || directAnswer.isBlank())
                && (sources == null || sources.isEmpty());
    }

    /** Flattened text handed to the validator. */
    public String asContext() {
        StringBuilder sb = new StringBuilder();
        if (directAnswer != null && !directAnswer.isBlank()) {
            sb.append("Provider answer: ").append(directAnswer).append("\n\n");
        }
        if (sources != null) {
            int i = 1;
            for (Source s : sources) {
                sb.append("Result ").append(i++).append(" (").append(s.url()).append("):\n")
                  .append(s.content() == null ? "" : s.content()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}
