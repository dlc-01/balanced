package io.github.balanced.common;

import java.util.List;

public record Pool(
        String name,
        BalancingAlgorithm algorithm,
        boolean stickyEnabled,
        int stickyTtlSeconds,
        List<Upstream> upstreams
) {}