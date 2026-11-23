package com.example.demo.embedding;

import com.example.demo.constants.Constants;
import com.example.demo.service.EmbeddingService;

import java.util.List;

public class FullEmbeddingDemo {

    public static void main(String[] args) throws Exception {

        String filePath =
                Constants.filePath;

        // STEP 1: Extract PDF pages
        PdfDocumentReader reader = new PdfDocumentReader();

        List<PdfDocumentReader.PageContent> pages =
                reader.extractPages(filePath);

        System.out.println(
                "Total pages: " + pages.size()
        );

        // STEP 2: Generate document chunks
        DocumentChunker chunker = new DocumentChunker();

        List<DocumentChunk> chunks =
                chunker.createChunks(
                        "System Design Interview by Alex Xu.pdf",
                        pages
                );

        System.out.println(
                "Total chunks: " + chunks.size()
        );

        // STEP 3: Generate embeddings in batches
        EmbeddingService embeddingService = null;
//                new EmbeddingService(); //TOTO

        BatchEmbeddingProcessor processor =
                new BatchEmbeddingProcessor(embeddingService);

        List<EmbeddedChunk> embeddedChunks =
                processor.process(chunks);

        VectorStorageService storageService =
                new VectorStorageService();

        storageService.saveAll(embeddedChunks);

        // STEP 4: Validate results
        System.out.println("\nEmbedding Summary");

        System.out.println(
                "Total embedded chunks: "
                        + embeddedChunks.size()
        );

        System.out.println(
                "First vector dimensions: "
                        + embeddedChunks.get(0).embedding().length
        );

        System.out.println("\nFirst embedded chunk:");

        EmbeddedChunk first = embeddedChunks.get(0);

        System.out.println(
                "Page: "
                        + first.documentChunk().pageNumber()
        );

        System.out.println(
                "Chunk: "
                        + first.documentChunk().chunkNumber()
        );

        System.out.println(
                "Content: "
                        + first.documentChunk().content()
        );
    }
}
