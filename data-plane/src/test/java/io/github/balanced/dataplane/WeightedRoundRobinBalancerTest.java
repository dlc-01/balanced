package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedRoundRobinBalancerTest {

    private static final InetAddress CLIENT = loopback();

    private final WeightedRoundRobinBalancer balancer = new WeightedRoundRobinBalancer();

    private static final Upstream HEAVY = new Upstream(1, "heavy", 8080, 3);
    private static final Upstream LIGHT = new Upstream(2, "light", 8080, 1);

    @Test
    void respectsWeightRatio() {
        var upstreams = List.of(HEAVY, LIGHT);
        Map<Upstream, Integer> counts = new HashMap<>();

        // totalWeight = 4, so 4 picks = one full cycle
        for (int i = 0; i < 400; i++) {
            counts.merge(balancer.pick(upstreams, CLIENT), 1, Integer::sum);
        }

        assertThat(counts.get(HEAVY)).isEqualTo(300); // 3/4
        assertThat(counts.get(LIGHT)).isEqualTo(100);  // 1/4
    }

    @Test
    void equalWeightsDistributeEvenly() {
        var a = new Upstream(1, "a", 8080, 5);
        var b = new Upstream(2, "b", 8080, 5);
        var upstreams = List.of(a, b);

        Map<Upstream, Integer> counts = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            counts.merge(balancer.pick(upstreams, CLIENT), 1, Integer::sum);
        }

        assertThat(counts.get(a)).isEqualTo(50);
        assertThat(counts.get(b)).isEqualTo(50);
    }

    @Test
    void singleUpstreamWithHighWeight() {
        var single = new Upstream(1, "solo", 8080, 10);
        for (int i = 0; i < 20; i++) {
            assertThat(balancer.pick(List.of(single), CLIENT)).isEqualTo(single);
        }
    }

    private static InetAddress loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}