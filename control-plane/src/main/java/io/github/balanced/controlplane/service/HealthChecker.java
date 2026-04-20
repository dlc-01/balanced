package io.github.balanced.controlplane.service;

import io.github.balanced.controlplane.entity.UpstreamEntity;
import io.github.balanced.controlplane.repository.UpstreamRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int UNHEALTHY_THRESHOLD = 3;
    private static final int HEALTHY_THRESHOLD = 2;

    private final UpstreamRepository upstreamRepository;
    private final ConfigSnapshotBuilder snapshotBuilder;
    private final MeterRegistry registry;

    private final Map<Long, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> successCounts = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> healthStatus = new ConcurrentHashMap<>();

    public HealthChecker(UpstreamRepository upstreamRepository,
                         ConfigSnapshotBuilder snapshotBuilder,
                         MeterRegistry registry) {
        this.upstreamRepository = upstreamRepository;
        this.snapshotBuilder = snapshotBuilder;
        this.registry = registry;
    }

    @Scheduled(fixedDelay = 5000)
    public void check() {
        boolean changed = false;

        for (UpstreamEntity upstream : upstreamRepository.findAll()) {
            String upstreamAddr = upstream.getHost() + ":" + upstream.getPort();
            MDC.put("upstream", upstreamAddr);

            Timer.Sample sample = Timer.start(registry);
            boolean reachable = probe(upstream);
            sample.stop(Timer.builder("lb_health_check_duration_seconds")
                    .tag("upstream", upstreamAddr)
                    .register(registry));

            boolean wasHealthy = healthStatus.getOrDefault(upstream.getId(), true);

            if (reachable) {
                failureCounts.computeIfAbsent(upstream.getId(), k -> new AtomicInteger()).set(0);
                int successes = successCounts.computeIfAbsent(upstream.getId(), k -> new AtomicInteger())
                        .incrementAndGet();

                if (!wasHealthy && successes >= HEALTHY_THRESHOLD) {
                    healthStatus.put(upstream.getId(), true);
                    changed = true;
                    log.info("Upstream is now HEALTHY");
                }
            } else {
                successCounts.computeIfAbsent(upstream.getId(), k -> new AtomicInteger()).set(0);
                int failures = failureCounts.computeIfAbsent(upstream.getId(), k -> new AtomicInteger())
                        .incrementAndGet();

                if (wasHealthy && failures >= UNHEALTHY_THRESHOLD) {
                    healthStatus.put(upstream.getId(), false);
                    changed = true;
                    log.warn("Upstream is now UNHEALTHY, consecutive_failures={}", failures);
                }
            }

            registry.gauge("lb_upstream_health",
                    Tags.of("upstream", upstreamAddr),
                    healthStatus.getOrDefault(upstream.getId(), true) ? 1.0 : 0.0);

            MDC.remove("upstream");
        }

        if (changed) {
            snapshotBuilder.rebuild();
        }
    }

    public boolean isHealthy(Long upstreamId) {
        return healthStatus.getOrDefault(upstreamId, true);
    }

    private boolean probe(UpstreamEntity upstream) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(upstream.getHost(), upstream.getPort()), CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}