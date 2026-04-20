package io.github.balanced.controlplane;

import io.github.balanced.common.BalancingAlgorithm;
import io.github.balanced.common.ConfigSnapshot;
import io.github.balanced.controlplane.entity.ListenerEntity;
import io.github.balanced.controlplane.entity.PoolEntity;
import io.github.balanced.controlplane.entity.UpstreamEntity;
import io.github.balanced.controlplane.repository.ListenerRepository;
import io.github.balanced.controlplane.repository.PoolRepository;
import io.github.balanced.controlplane.repository.UpstreamRepository;
import io.github.balanced.controlplane.service.ConfigSnapshotBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class ConfigSnapshotBuilderIntegrationTest {

    @Autowired ConfigSnapshotBuilder snapshotBuilder;
    @Autowired UpstreamRepository upstreamRepo;
    @Autowired PoolRepository poolRepo;
    @Autowired ListenerRepository listenerRepo;

    @BeforeEach
    void cleanDb() {
        listenerRepo.deleteAll();
        poolRepo.deleteAll();
        upstreamRepo.deleteAll();
    }

    @Test
    void emptyDbProducesEmptySnapshot() {
        snapshotBuilder.rebuild();
        ConfigSnapshot snap = snapshotBuilder.current();

        assertThat(snap.pools()).isEmpty();
        assertThat(snap.listeners()).isEmpty();
    }

    @Test
    void rebuildCapturesFullConfig() {
        var u1 = new UpstreamEntity();
        u1.setHost("10.0.0.1");
        u1.setPort(9001);
        u1.setWeight(2);
        u1 = upstreamRepo.save(u1);

        var pool = new PoolEntity();
        pool.setName("web");
        pool.setAlgorithm(BalancingAlgorithm.ROUND_ROBIN);
        pool.setUpstreams(List.of(u1));
        pool = poolRepo.save(pool);

        var listener = new ListenerEntity();
        listener.setPort(8080);
        listener.setPool(pool);
        listenerRepo.save(listener);

        snapshotBuilder.rebuild();
        ConfigSnapshot snap = snapshotBuilder.current();

        assertThat(snap.version()).isPositive();
        assertThat(snap.pools()).containsKey("web");
        assertThat(snap.pools().get("web").upstreams()).hasSize(1);
        assertThat(snap.pools().get("web").upstreams().getFirst().host()).isEqualTo("10.0.0.1");
        assertThat(snap.listeners()).hasSize(1);
        assertThat(snap.listeners().getFirst().poolName()).isEqualTo("web");
    }

    @Test
    void versionIncrementsOnEachRebuild() {
        snapshotBuilder.rebuild();
        long v1 = snapshotBuilder.current().version();

        snapshotBuilder.rebuild();
        long v2 = snapshotBuilder.current().version();

        assertThat(v2).isGreaterThan(v1);
    }
}