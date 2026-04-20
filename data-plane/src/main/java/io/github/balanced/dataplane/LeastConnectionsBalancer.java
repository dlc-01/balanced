package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class LeastConnectionsBalancer implements LoadBalancer {

    private final Map<Long, AtomicInteger> active = new ConcurrentHashMap<>();

    @Override
    public Upstream pick(List<Upstream> healthy, InetAddress clientAddress) {
        Upstream best = null;
        int min = Integer.MAX_VALUE;

        for (Upstream u : healthy) {
            int count = active.computeIfAbsent(u.id(), k -> new AtomicInteger()).get();
            if (count < min) {
                min = count;
                best = u;
            }
        }
        return best;
    }

    public void onConnect(Upstream upstream) {
        active.computeIfAbsent(upstream.id(), k -> new AtomicInteger()).incrementAndGet();
    }

    public void onDisconnect(Upstream upstream) {
        AtomicInteger counter = active.get(upstream.id());
        if (counter != null) {
            counter.decrementAndGet();
        }
    }
}