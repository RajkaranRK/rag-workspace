package com.example.demo.controller;

import com.example.demo.learning.UnansweredLearningService;
import com.example.demo.learning.UnansweredLearningService.GenerateResult;
import com.example.demo.learning.UnansweredQueryRepository;
import com.example.demo.model.UnansweredQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phase-2 control surface for the learning pipeline.
 *   GET  /api/v1/learning/unanswered?status=PENDING&limit=50   list recorded questions
 *   POST /api/v1/learning/unanswered/{id}/generate             generate+publish an answer
 *   POST /api/v1/learning/unanswered/process?limit=10          process all PENDING
 */
@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {

    private final UnansweredQueryRepository repository;
    private final UnansweredLearningService learningService;

    public LearningController(
            UnansweredQueryRepository repository,
            UnansweredLearningService learningService
    ) {
        this.repository = repository;
        this.learningService = learningService;
    }

    @GetMapping("/unanswered")
    public List<UnansweredQuery> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return repository.list(status, Math.min(Math.max(limit, 1), 500));
    }

    @PostMapping("/unanswered/{id}/generate")
    public ResponseEntity<GenerateResult> generate(@PathVariable long id) throws Exception {
        return learningService.generate(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/unanswered/process")
    public List<GenerateResult> process(@RequestParam(defaultValue = "10") int limit) {
        return learningService.processPending(Math.min(Math.max(limit, 1), 100));
    }
}
