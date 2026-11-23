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

            String text = page.text();

            int start = 0;

            while (start < text.length()) {

                int end = Math.min(
                        start + CHUNK_SIZE,
                        text.length()
                );

                String content = text.substring(start, end);

                chunks.add(new DocumentChunk(
                        documentName,
                        page.pageNumber(),
                        chunkNumber++,
                        content
                ));

                if (end == text.length()) {
                    break;
                }

                start = end - OVERLAP;
            }
        }

        return chunks;
    }
}
