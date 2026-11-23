package com.example.demo.llm;

import com.example.demo.query.SearchResult;

import java.util.List;

public class RagPromptBuilder {

    public String buildPrompt(
            String question,
            List<SearchResult> results
    ) {

        StringBuilder context = new StringBuilder();

        for (SearchResult result : results) {

            context.append("\n")
                    .append("[Page ")
                    .append(result.pageNumber())
                    .append("]\n");

            context.append(result.content())
                    .append("\n");
        }

        return """
                You are a system design tutor.

                Answer the user's question using the provided context
                from the book.

                Instructions:
                1. Prioritize information from the provided context.
                2. Do not invent facts or page references.
                3. If the context does not contain enough information,
                   clearly say so.
                4. Explain concepts in beginner-friendly language.
                5. Include relevant page numbers in your answer.

                BOOK CONTEXT:
                %s

                USER QUESTION:
                %s

                ANSWER:
                """.formatted(context, question);
    }
}
