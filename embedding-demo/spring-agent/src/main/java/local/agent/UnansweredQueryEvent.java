package local.agent;

/** Phase-1 event: a question the book could not answer, recorded for later learning. */
public record UnansweredQueryEvent(
        String eventId,
        String question,
        String createdAt
) {}
