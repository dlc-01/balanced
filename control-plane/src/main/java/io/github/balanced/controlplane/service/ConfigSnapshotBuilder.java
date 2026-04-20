package io.github.balanced.controlplane.service;

import io.github.balanced.common.ConfigProvider;
import io.github.balanced.common.ConfigSnapshot;
import io.github.balanced.common.Listener;
import io.github.balanced.common.Pool;
import io.github.balanced.common.Upstream;
import io.github.balanced.controlplane.entity.ListenerEntity;
import io.github.balanced.controlplane.entity.PoolEntity;
import io.github.balanced.controlplane.repository.ListenerRepository;
import io.github.balanced.controlplane.repository.PoolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ConfigSnapshotBuilder implements ConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(ConfigSnapshotBuilder.class);

    private final PoolRepository poolRepository;
    private final ListenerRepository listenerRepository;

    private final AtomicReference<ConfigSnapshot> ref = new AtomicReference<>(
            new ConfigSnapshot(java.util.List.of(), Map.of(), 0)
    );
    private final AtomicLong versionCounter = new AtomicLong();

    public ConfigSnapshotBuilder(PoolRepository poolRepository, ListenerRepository listenerRepository) {
        this.poolRepository = poolRepository;
        this.listenerRepository = listenerRepository;
    }

    @Override
    public ConfigSnapshot current() {
        return ref.get();
    }

    @Transactional(readOnly = true)
    public void rebuild() {
        rebuild(java.util.Set.of());
    }

    @Transactional(readOnly = true)
    public void rebuild(java.util.Set<Long> unhealthyIds) {
        Map<String, Pool> pools = poolRepository.findAll().stream()
                .collect(Collectors.toMap(
                        PoolEntity::getName,
                        entity -> toPool(entity, unhealthyIds)
                ));

        var listeners = listenerRepository.findAll().stream()
                .map(this::toListener)
                .toList();

        long version = versionCounter.incrementAndGet();
        ConfigSnapshot snapshot = new ConfigSnapshot(listeners, pools, version);
        ref.set(snapshot);

        log.info("Config snapshot rebuilt, version={}, pools={}, listeners={}",
                version, pools.size(), listeners.size());
    }

    private Pool toPool(PoolEntity entity, java.util.Set<Long> unhealthyIds) {
        var upstreams = entity.getUpstreams().stream()
                .filter(u -> !unhealthyIds.contains(u.getId()))
                .map(u -> new Upstream(u.getId(), u.getHost(), u.getPort(), u.getWeight()))
                .toList();
        return new Pool(entity.getName(), entity.getAlgorithm(),
                entity.isStickyEnabled(), entity.getStickyTtlSeconds(), upstreams);
    }

    private Listener toListener(ListenerEntity entity) {
        return new Listener(entity.getPort(), entity.getPool().getName());
    }
}