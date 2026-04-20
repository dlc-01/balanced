package io.github.balanced.controlplane.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateUpstreamRequest(
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @Min(1) @Max(100) int weight
) {}