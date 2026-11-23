package com.example.demo.repository;

import com.example.demo.query.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public VectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchResult> search(
            double[] queryEmbedding,
            int limit
    ) {

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

        String vectorString = toVectorString(queryEmbedding);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SearchResult(
                        rs.getLong("id"),
                        rs.getString("document_name"),
                        rs.getInt("page_number"),
                        rs.getInt("chunk_number"),
                        rs.getString("content"),
                        rs.getDouble("cosine_similarity")
                ),
                vectorString,
                limit
        );
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
