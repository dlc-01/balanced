package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinBalancerTest {

    private static final InetAddress CLIENT = loopback();

    private final RoundRobinBalancer balancer = new RoundRobinBalancer();

    private static final Upstream A = new Upstream(1, "a", 8080, 1);
    private static final Upstream B = new Upstream(2, "b", 8080, 1);
    private static final Upstream C = new Upstream(3, "c", 8080, 1);

    @Test
    void cyclesThroughUpstreamsEvenly() {
        var upstreams = List.of(A, B, C);

        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(A);
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(B);
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(C);
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(A);
    }

    @Test
    void distributionIsUniformOver1000Picks() {
        var upstreams = List.of(A, B, C);
        Map<Upstream, Integer> counts = new HashMap<>();

        for (int i = 0; i < 999; i++) {
            counts.merge(balancer.pick(upstreams, CLIENT), 1, Integer::sum);
        }

        assertThat(counts.get(A)).isEqualTo(333);
        assertThat(counts.get(B)).isEqualTo(333);
        assertThat(counts.get(C)).isEqualTo(333);
    }

    @Test
    void singleUpstreamAlwaysReturned() {
        var upstreams = List.of(A);
        for (int i = 0; i < 10; i++) {
            assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(A);
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