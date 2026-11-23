package com.example.demo.model;

import java.util.List;

public record EvaluationQuestion(
        String question,
        List<Long> expectedChunkIds
) {}