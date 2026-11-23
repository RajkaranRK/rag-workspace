package com.example.demo.embedding;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

public class VectorStorageService {

    private static final String URL =
            "jdbc:postgresql://localhost:5432/ragdb";

    private static final String USER = "raguser";

    private static final String PASSWORD = "ragpassword";

    private static final int BATCH_SIZE = 100;

    public void saveAll(List<EmbeddedChunk> embeddedChunks)
            throws Exception {

        String sql = """
                INSERT INTO document_chunks (
                    document_name,
                    page_number,
                    chunk_number,
                    content,
                    embedding
                )
                VALUES (?, ?, ?, ?, ?::vector)
                """;

        try (Connection connection =
                     DriverManager.getConnection(URL, USER, PASSWORD)) {

            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                int count = 0;

                for (EmbeddedChunk embeddedChunk : embeddedChunks) {

                    DocumentChunk chunk =
                            embeddedChunk.documentChunk();

                    statement.setString(1, chunk.documentName());
                    statement.setInt(2, chunk.pageNumber());
                    statement.setInt(3, chunk.chunkNumber());
                    statement.setString(4, chunk.content());

                    String vector = toVectorString(
                            embeddedChunk.embedding()
                    );

                    statement.setString(5, vector);

                    statement.addBatch();

                    count++;

                    if (count % BATCH_SIZE == 0) {
                        statement.executeBatch();
                        System.out.println(
                                "Inserted " + count + " chunks"
                        );
                    }
                }

                statement.executeBatch();

                connection.commit();

                System.out.println(
                        "Successfully stored " +
                                embeddedChunks.size() + " embeddings"
                );

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
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
