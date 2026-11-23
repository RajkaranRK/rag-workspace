package com.example.demo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(

        @NotBlank(message = "Question cannot be empty")
        @Size(
                min = 3,
                max = 1000,
                message = "Question must be between 3 and 1000 characters"
        )
        String question
) {}
