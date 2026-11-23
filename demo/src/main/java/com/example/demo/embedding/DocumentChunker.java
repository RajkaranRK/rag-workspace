package com.example.demo.embedding;

import java.util.ArrayList;
import java.util.List;

public class DocumentChunker {

    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 200;

    public List<DocumentChunk> createChunks(
            String documentName,
            List<PdfDocumentReader.PageContent> pages) {

        List<DocumentChunk> chunks = new ArrayList<>();

        int chunkNumber = 1;

        for (PdfDocumentReader.PageContent page : pages) {
            for (String content : splitText(page.text())) {
                chunks.add(new DocumentChunk(
                        documentName,
                        page.pageNumber(),
                        chunkNumber++,
                        content
                ));
            }
        }

        return chunks;
    }

    /**
     * The single chunking rule used across the app: fixed-size windows of
     * CHUNK_SIZE characters with OVERLAP characters repeated between adjacent
     * windows. Both PDF ingestion and learned-answer ingestion use this so the
     * two paths stay identical.
     */
    public List<String> splitText(String text) {

        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int start = 0;
        int length = text.length();

        while (start < length) {

            int end = Math.min(start + CHUNK_SIZE, length);

            chunks.add(text.substring(start, end));

            if (end == length) {
                break;
            }

            start = end - OVERLAP;
        }

        return chunks;
    }
}
