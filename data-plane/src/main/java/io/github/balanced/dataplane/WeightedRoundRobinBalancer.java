package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class WeightedRoundRobinBalancer implements LoadBalancer {

    private final AtomicInteger index = new AtomicInteger();

    @Override
    public Upstream pick(List<Upstream> healthy, InetAddress clientAddress) {
        int totalWeight = healthy.stream().mapToInt(Upstream::weight).sum();
        int pos = Math.floorMod(index.getAndIncrement(), totalWeight);

        for (Upstream u : healthy) {
            pos -= u.weight();
            if (pos < 0) {
                return u;
            }
        }
        return healthy.getLast();
    }
}
