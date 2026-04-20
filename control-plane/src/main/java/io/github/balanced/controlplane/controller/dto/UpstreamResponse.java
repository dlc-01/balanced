package io.github.balanced.controlplane.controller.dto;

import io.github.balanced.controlplane.entity.UpstreamEntity;

import java.time.Instant;

public record UpstreamResponse(
        Long id,
        String host,
        int port,
        int weight,
        Instant createdAt
) {
    public static UpstreamResponse from(UpstreamEntity entity) {
        return new UpstreamResponse(
                entity.getId(), entity.getHost(), entity.getPort(),
                entity.getWeight(), entity.getCreatedAt());
    }
}