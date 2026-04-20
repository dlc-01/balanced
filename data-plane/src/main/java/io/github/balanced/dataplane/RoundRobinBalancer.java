package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinBalancer implements LoadBalancer {

    private final AtomicInteger index = new AtomicInteger();

    @Override
    public Upstream pick(List<Upstream> healthy, InetAddress clientAddress) {
        int i = Math.floorMod(index.getAndIncrement(), healthy.size());
        return healthy.get(i);
    }
}
