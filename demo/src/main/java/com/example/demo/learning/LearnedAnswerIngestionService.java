package com.example.demo.learning;

import com.example.demo.embedding.DocumentChunker;
import com.example.demo.model.LearnedAnswerEvent;
import com.example.demo.repository.VectorRepository;
import com.example.demo.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Turns a validated, web-sourced answer into vector-store rows.
 *
 * Steps: build a question-aware text, split into overlapping chunks, embed each
 * with the document prefix, skip the whole event if a near-identical vector is
 * already stored (so we only learn once), then insert. Learned rows use
 * document_name = "web-learned" and negative chunk numbers so they never clash
 * with the positive book chunk IDs used by the evaluation endpoints.
 */
@Service
public class LearnedAnswerIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(LearnedAnswerIngestionService.class);

    private final DocumentChunker chunker = new DocumentChunker();
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final String documentName;
    private final double dedupThreshold;

    public LearnedAnswerIngestionService(
            EmbeddingService embeddingService,
            VectorRepository vectorRepository,
            @Value("${learning.document-name:web-learned}") String documentName,
            @Value("${learning.dedup-threshold:0.97}") double dedupThreshold
    ) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.documentName = documentName;
        this.dedupThreshold = dedupThreshold;
    }

    public void ingest(LearnedAnswerEvent event) throws Exception {
        if (event == null || event.answer() == null || event.answer().isBlank()) {
            log.warn("Skipping learned answer with no content eventId={}",
                    event == null ? "null" : event.eventId());
            return;
        }

        log.info("Ingesting learned answer eventId={} question='{}' answerChars={}",
                event.eventId(), event.question(), event.answer().length());

        String text = buildText(event);
        List<String> chunks = chunker.splitText(text);
        log.info("Prepared eventId={} chunks={} dedupThreshold={}",
                event.eventId(), chunks.size(), dedupThreshold);
        if (chunks.isEmpty()) {
            return;
        }

        // Dedup on the first chunk: if we already hold a near-identical vector,
        // the answer was learned before and we stop here.
        double[] firstEmbedding =
                embeddingService.generateDocumentEmbedding(chunks.get(0));
        double maxSim = vectorRepository.maxSimilarity(firstEmbedding);
        if (maxSim >= dedupThreshold) {
            log.info("Already learned (similarity={}), skipping eventId={} question='{}'",
                    maxSim, event.eventId(), event.question());
            return;
        }

        int stored = 0;
        for (int i = 0; i < chunks.size(); i++) {
            double[] embedding = (i == 0)
                    ? firstEmbedding
                    : embeddingService.generateDocumentEmbedding(chunks.get(i));
            int chunkNumber = -(i + 1); // negative => learned, not a book chunk
            vectorRepository.insertChunk(
                    documentName, 0, chunkNumber, chunks.get(i), embedding);
            stored++;
        }

        log.info("Learned answer stored eventId={} chunks={} question='{}' sources={}",
                event.eventId(), stored, event.question(), event.sourceUrls());
    }

    /** Prepend the question (and sources) so the stored vector captures intent. */
    private String buildText(LearnedAnswerEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question: ").append(event.question().trim()).append("\n\n");
        sb.append("Answer: ").append(event.answer().trim());
        if (event.sourceUrls() != null && !event.sourceUrls().isEmpty()) {
            sb.append("\n\nSources: ").append(String.join(", ", event.sourceUrls()));
        }
        return sb.toString();
    }

}
