package io.github.balanced.controlplane.controller.dto;

import io.github.balanced.common.BalancingAlgorithm;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreatePoolRequest(
        @NotBlank String name,
        @NotNull BalancingAlgorithm algorithm,
        boolean stickyEnabled,
        @Min(0) int stickyTtlSeconds,
        List<Long> upstreamIds
) {}