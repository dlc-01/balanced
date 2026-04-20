package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;

import java.net.InetAddress;
import java.util.List;

/**
 * Strategy interface — each algorithm is a separate implementation.
 */
public interface LoadBalancer {

    Upstream pick(List<Upstream> healthy, InetAddress clientAddress);
}
