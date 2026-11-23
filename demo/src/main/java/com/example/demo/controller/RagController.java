package com.example.demo.controller;

import com.example.demo.model.AskRequest;
import com.example.demo.model.AskResponse;
import com.example.demo.model.AgentAskResponse;
import com.example.demo.model.EvaluationComparisonResult;
import com.example.demo.model.EvaluationQuestion;
import com.example.demo.service.RagEvaluationService;
import com.example.demo.service.RagService;
import com.example.demo.service.AgenticRagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagService ragService;
    private final RagEvaluationService ragEvaluationService;
    private final AgenticRagService agenticRagService;

    public RagController(
            RagService ragService,
            RagEvaluationService ragEvaluationService,
            AgenticRagService agenticRagService
    ) {
        this.ragService = ragService;
        this.ragEvaluationService = ragEvaluationService;
        this.agenticRagService = agenticRagService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request)
            throws Exception {
        return ragService.ask(request.question());
    }

    @PostMapping("/agent/ask")
    public AgentAskResponse askWithAgent(@Valid @RequestBody AskRequest request)
            throws Exception {
        return agenticRagService.ask(request.question());
    }


    @PostMapping("/evaluate/compare")
    public EvaluationComparisonResult compare(
            @Valid @RequestBody EvaluationQuestion request,
            @RequestParam(defaultValue = "5") int k
    ) throws Exception {
        return ragEvaluationService.compare(request, k);
    }
}
