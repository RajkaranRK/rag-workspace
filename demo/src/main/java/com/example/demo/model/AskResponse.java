package com.example.demo.model;

import java.util.List;

public record AskResponse(
        String answer,
        List<Source> sources
) {


    public record Source(
            String documentName,
            int pageNumber,
            int chunkNumber,
            double similarity
    ) {}
}
