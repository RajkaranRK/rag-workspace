package com.example.demo.model;

import java.util.List;

/** The answer plus a small, inspectable trace of the agent's search steps. */
public record AgentAskResponse(
        String answer,
        List<AskResponse.Source> sources,
        List<String> searchedQueries
) {}
