package com.example.demo.controller;

import com.example.demo.model.EvaluationQuestion;
import com.example.demo.model.EvaluationResult;
import com.example.demo.service.RagEvaluationService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
public class RagEvaluationController {

    private final RagEvaluationService evaluationService;

    public RagEvaluationController(
            RagEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public EvaluationResult evaluate(
            @RequestBody EvaluationQuestion request,
            @RequestParam(defaultValue = "5") int k
    ) throws Exception {

        if (k <= 0) {
            throw new IllegalArgumentException("K must be greater than zero");
        }

        return evaluationService.evaluate(request, k);
    }
}
