package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeastConnectionsBalancerTest {

    private static final InetAddress CLIENT = loopback();

    private final LeastConnectionsBalancer balancer = new LeastConnectionsBalancer();

    private static final Upstream A = new Upstream(1, "a", 8080, 1);
    private static final Upstream B = new Upstream(2, "b", 8080, 1);
    private static final Upstream C = new Upstream(3, "c", 8080, 1);

    @Test
    void picksUpstreamWithFewestConnections() {
        var upstreams = List.of(A, B, C);

        // All zero — picks first
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(A);

        // Simulate A has 2 connections, B has 1
        balancer.onConnect(A);
        balancer.onConnect(A);
        balancer.onConnect(B);

        // Should pick C (0 connections)
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(C);
    }

    @Test
    void disconnectReducesCount() {
        var upstreams = List.of(A, B);

        balancer.onConnect(A);
        balancer.onConnect(A);
        balancer.onConnect(B);

        // A=2, B=1 → picks B
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(B);

        balancer.onDisconnect(A);
        balancer.onDisconnect(A);

        // A=0, B=1 → picks A
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(A);
    }

    @Test
    void allEqualPicksFirst() {
        var upstreams = List.of(A, B, C);

        balancer.onConnect(A);
        balancer.onConnect(B);
        balancer.onConnect(C);

        // All have 1 connection — deterministically picks first (A)
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(A);
    }

    @Test
    void handlesNewUpstreamWithZeroConnections() {
        balancer.onConnect(A);
        balancer.onConnect(A);
        balancer.onConnect(B);

        var newUpstream = new Upstream(99, "new", 8080, 1);
        var upstreams = List.of(A, B, newUpstream);

        // new upstream has 0 connections — should be picked
        assertThat(balancer.pick(upstreams, CLIENT)).isEqualTo(newUpstream);
    }

    private static InetAddress loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}