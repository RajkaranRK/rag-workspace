package com.example.demo.archived;

import com.example.demo.constants.Constants;
import com.example.demo.embedding.DocumentChunk;
import com.example.demo.embedding.DocumentChunker;
import com.example.demo.embedding.PdfDocumentReader;

import java.util.List;

public class ChunkingDemo {

    public static void main(String[] args) throws Exception {

        String filePath = Constants.filePath;

        PdfDocumentReader reader = new PdfDocumentReader();

        List<PdfDocumentReader.PageContent> pages =
                reader.extractPages(filePath);

        DocumentChunker chunker = new DocumentChunker();

        List<DocumentChunk> chunks = chunker.createChunks(
                "System Design Interview by Alex Xu.pdf",
                pages
        );

        System.out.println("Total PDF pages: " + pages.size());

        System.out.println("Total chunks: " + chunks.size());

        System.out.println("\nFirst 5 chunks:");

        chunks.stream()
                .limit(5)
                .forEach(chunk -> {

                    System.out.println("\n----------------------");

                    System.out.println("Document: " + chunk.documentName());

                    System.out.println("Page: " + chunk.pageNumber());

                    System.out.println("Chunk: " + chunk.chunkNumber());

                    System.out.println("Content: " + chunk.content());
                });
    }
}
