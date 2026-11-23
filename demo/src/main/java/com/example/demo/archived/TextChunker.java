package com.example.demo.archived;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    public static List<String> chunkText(
            String text,
            int chunkSize,
            int overlap) {

        List<String> chunks = new ArrayList<>();

        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "Invalid chunk size or overlap"
            );
        }

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(
                    start + chunkSize,
                    text.length()
            );

            chunks.add(text.substring(start, end));

            if (end == text.length()) {
                break;
            }

            start = end - overlap;
        }

        return chunks;
    }

    public static void main(String[] args) {

        String text = """
                Consistent hashing is a technique used in distributed systems.
                It helps distribute data across multiple nodes.
                When a node is added or removed, only a small portion
                of the keys need to be redistributed.
                """;

        List<String> chunks = chunkText(text, 100, 20);

        for (int i = 0; i < chunks.size(); i++) {

            System.out.println("Chunk " + (i + 1));

            System.out.println(chunks.get(i));

            System.out.println("-------------------");
        }
    }
}
