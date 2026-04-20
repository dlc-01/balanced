package io.github.balanced.controlplane.controller.dto;

import io.github.balanced.common.BalancingAlgorithm;
import io.github.balanced.controlplane.entity.PoolEntity;

import java.util.List;

public record PoolResponse(
        Long id,
        String name,
        BalancingAlgorithm algorithm,
        boolean stickyEnabled,
        int stickyTtlSeconds,
        List<UpstreamResponse> upstreams
) {
    public static PoolResponse from(PoolEntity entity) {
        var upstreams = entity.getUpstreams().stream()
                .map(UpstreamResponse::from)
                .toList();
        return new PoolResponse(
                entity.getId(), entity.getName(), entity.getAlgorithm(),
                entity.isStickyEnabled(), entity.getStickyTtlSeconds(), upstreams);
    }
}