package io.github.balanced.common;

import java.util.List;
import java.util.Map;

public record ConfigSnapshot(
        List<Listener> listeners,
        Map<String, Pool> pools,
        long version
) {}