package io.github.balanced.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigSnapshotTest {

    @Test
    void snapshotIsImmutableRecord() {
        var upstream = new Upstream(1, "host", 8080, 1);
        var pool = new Pool("web", BalancingAlgorithm.ROUND_ROBIN, false, 0, List.of(upstream));
        var listener = new Listener(8080, "web");

        var snapshot = new ConfigSnapshot(List.of(listener), Map.of("web", pool), 1);

        assertThat(snapshot.version()).isEqualTo(1);
        assertThat(snapshot.pools()).containsKey("web");
        assertThat(snapshot.listeners()).hasSize(1);
    }

    @Test
    void poolUpstreamsListIsImmutableIfPassedAsUnmodifiable() {
        var upstream = new Upstream(1, "host", 8080, 1);
        var pool = new Pool("web", BalancingAlgorithm.ROUND_ROBIN, false, 0, List.of(upstream));

        assertThatThrownBy(() -> pool.upstreams().add(new Upstream(2, "h2", 8081, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void upstreamAddressFormat() {
        var u = new Upstream(1, "10.0.0.1", 9090, 3);
        assertThat(u.address()).isEqualTo("10.0.0.1:9090");
    }

    @Test
    void emptySnapshotIsValid() {
        var snapshot = new ConfigSnapshot(List.of(), Map.of(), 0);
        assertThat(snapshot.pools()).isEmpty();
        assertThat(snapshot.listeners()).isEmpty();
        assertThat(snapshot.version()).isZero();
    }
}