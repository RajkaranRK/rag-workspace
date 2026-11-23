package com.example.demo.query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class VectorSearchService {

    private static final String URL =
            "jdbc:postgresql://localhost:5432/ragdb";

    private static final String USER = "raguser";

    private static final String PASSWORD = "ragpassword";

    public List<SearchResult> search(
            double[] queryEmbedding,
            int limit
    ) throws Exception {

        if (queryEmbedding.length != 768) {
            throw new IllegalArgumentException(
                    "Expected 768 dimensions, received: "
                            + queryEmbedding.length
            );
        }

        String sql = """
                WITH query_embedding AS (
                    SELECT CAST(? AS vector) AS embedding
                )
                SELECT
                    dc.id,
                    dc.document_name,
                    dc.page_number,
                    dc.chunk_number,
                    dc.content,
                    1 - (dc.embedding <=> qe.embedding)
                        AS cosine_similarity
                FROM document_chunks dc
                CROSS JOIN query_embedding qe
                ORDER BY dc.embedding <=> qe.embedding
                LIMIT ?
                """;

        List<SearchResult> results = new ArrayList<>();

        try (Connection connection =
                     DriverManager.getConnection(URL, USER, PASSWORD);

             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, toVectorString(queryEmbedding));
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {

                    SearchResult result = new SearchResult(
                            resultSet.getLong("id"),
                            resultSet.getString("document_name"),
                            resultSet.getInt("page_number"),
                            resultSet.getInt("chunk_number"),
                            resultSet.getString("content"),
                            resultSet.getDouble("cosine_similarity")
                    );

                    results.add(result);
                }
            }
        }

        return results;
    }

    private String toVectorString(double[] embedding) {

        StringBuilder vector = new StringBuilder("[");

        for (int i = 0; i < embedding.length; i++) {

            if (i > 0) {
                vector.append(",");
            }

            vector.append(embedding[i]);
        }

        vector.append("]");

        return vector.toString();
    }
}
