package io.github.balanced.dataplane;

import io.github.balanced.common.BalancingAlgorithm;
import io.github.balanced.common.ConfigProvider;
import io.github.balanced.common.ConfigSnapshot;
import io.github.balanced.common.Listener;
import io.github.balanced.common.Pool;
import io.github.balanced.common.Upstream;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DataPlane implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DataPlane.class);

    private final ConfigProvider configProvider;
    private final MeterRegistry registry;

    private final Map<BalancingAlgorithm, LoadBalancer> balancers = new EnumMap<>(BalancingAlgorithm.class);
    private final Map<InetAddress, Upstream> stickyMap = new ConcurrentHashMap<>();

    private final AtomicInteger activeConnections = new AtomicInteger();

    private volatile boolean running = true;

    public DataPlane(ConfigProvider configProvider, MeterRegistry registry) {
        this.configProvider = configProvider;
        this.registry = registry;

        balancers.put(BalancingAlgorithm.ROUND_ROBIN, new RoundRobinBalancer());
        balancers.put(BalancingAlgorithm.WEIGHTED_ROUND_ROBIN, new WeightedRoundRobinBalancer());
        balancers.put(BalancingAlgorithm.LEAST_CONNECTIONS, new LeastConnectionsBalancer());

        registry.gauge("lb_active_connections", activeConnections);
    }

    @Override
    public void run() {
        try {
            eventLoop();
        } catch (IOException e) {
            log.error("Data plane event loop failed", e);
        }
    }

    public void shutdown() {
        running = false;
    }

    private void eventLoop() throws IOException {
        ConfigSnapshot config = configProvider.current();
        MDC.put("configVersion", String.valueOf(config.version()));

        try (Selector selector = Selector.open()) {
            for (Listener listener : config.listeners()) {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.bind(new InetSocketAddress(listener.port()));
                server.register(selector, SelectionKey.OP_ACCEPT, listener.poolName());
                MDC.put("pool", listener.poolName());
                log.info("Listening on :{}", listener.port());
                MDC.remove("pool");
            }

            while (running) {
                selector.select(1000);
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key, selector);
                        } else if (key.isConnectable()) {
                            handleConnect(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        log.warn("I/O error on key, cancelling", e);
                        key.cancel();
                        key.channel().close();
                    }
                }
            }

            log.info("Data plane shutting down gracefully, active_connections={}", activeConnections.get());
        } finally {
            MDC.clear();
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        if (client == null) return;

        client.configureBlocking(false);
        String poolName = (String) key.attachment();
        MDC.put("pool", poolName);

        ConfigSnapshot config = configProvider.current();
        MDC.put("configVersion", String.valueOf(config.version()));

        Pool pool = config.pools().get(poolName);
        if (pool == null || pool.upstreams().isEmpty()) {
            log.warn("No upstreams available, rejecting connection");
            client.close();
            MDC.remove("pool");
            return;
        }

        InetAddress clientAddr = ((InetSocketAddress) client.getRemoteAddress()).getAddress();
        Upstream target = pickUpstream(pool, clientAddr);
        MDC.put("upstream", target.address());

        try {
            SocketChannel upstream = SocketChannel.open();
            upstream.configureBlocking(false);
            upstream.connect(new InetSocketAddress(target.host(), target.port()));

            ConnectionPair pair = new ConnectionPair(client, upstream, target, poolName);
            upstream.register(selector, SelectionKey.OP_CONNECT, pair);

            activeConnections.incrementAndGet();
            Counter.builder("lb_connections_total")
                    .tag("pool", poolName).tag("status", "accepted")
                    .register(registry).increment();

            log.info("Connection accepted, client={}", clientAddr.getHostAddress());
        } catch (IOException e) {
            log.warn("Failed to connect to upstream", e);
            client.close();
        } finally {
            MDC.remove("pool");
            MDC.remove("upstream");
        }
    }

    private Upstream pickUpstream(Pool pool, InetAddress clientAddr) {
        List<Upstream> healthy = pool.upstreams();

        if (pool.stickyEnabled()) {
            Upstream sticky = stickyMap.get(clientAddr);
            if (sticky != null && healthy.contains(sticky)) {
                return sticky;
            }
        }

        LoadBalancer lb = balancers.get(pool.algorithm());
        Upstream chosen = lb.pick(healthy, clientAddr);

        if (pool.stickyEnabled()) {
            stickyMap.put(clientAddr, chosen);
        }
        return chosen;
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel upstream = (SocketChannel) key.channel();
        ConnectionPair pair = (ConnectionPair) key.attachment();

        if (upstream.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ);
            pair.clientChannel.register(key.selector(), SelectionKey.OP_READ, pair);
            MDC.put("pool", pair.poolName);
            MDC.put("upstream", pair.upstream.address());
            log.debug("Upstream connection established");
            MDC.remove("pool");
            MDC.remove("upstream");
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionPair pair = (ConnectionPair) key.attachment();

        boolean isClient = (channel == pair.clientChannel);
        var buffer = isClient ? pair.clientToUpstream : pair.upstreamToClient;
        SocketChannel target = isClient ? pair.upstreamChannel : pair.clientChannel;

        int read = channel.read(buffer);
        if (read == -1) {
            closeConnection(pair, key);
            return;
        }

        buffer.flip();
        target.write(buffer);
        buffer.compact();

        Counter.builder("lb_bytes_total")
                .tag("pool", pair.poolName)
                .tag("direction", isClient ? "inbound" : "outbound")
                .register(registry).increment(read);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        // TODO: implement back-pressure write handling
    }

    private void closeConnection(ConnectionPair pair, SelectionKey key) {
        MDC.put("pool", pair.poolName);
        MDC.put("upstream", pair.upstream.address());

        activeConnections.decrementAndGet();
        try { pair.clientChannel.close(); } catch (IOException ignored) {}
        try { pair.upstreamChannel.close(); } catch (IOException ignored) {}
        key.cancel();

        LoadBalancer lb = balancers.get(
                configProvider.current().pools().get(pair.poolName).algorithm());
        if (lb instanceof LeastConnectionsBalancer lcb) {
            lcb.onDisconnect(pair.upstream);
        }

        log.debug("Connection closed");
        MDC.remove("pool");
        MDC.remove("upstream");
    }
}