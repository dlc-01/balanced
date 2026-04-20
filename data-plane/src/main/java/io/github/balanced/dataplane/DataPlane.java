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
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DataPlane implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DataPlane.class);

    private final ConfigProvider configProvider;
    private final MeterRegistry registry;

    private final Map<BalancingAlgorithm, LoadBalancer> balancers = new EnumMap<>(BalancingAlgorithm.class);
    private final Map<InetAddress, Upstream> stickyMap = new ConcurrentHashMap<>();
    private final Map<InetAddress, Instant> stickyTimestamps = new ConcurrentHashMap<>();
    private Instant lastStickyCleanup = Instant.now();

    private static final long CONNECT_TIMEOUT_MS = 5000;

    private final AtomicInteger activeConnections = new AtomicInteger();
    private final Map<SelectionKey, Long> pendingConnects = new HashMap<>();

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
        long knownVersion = -1;
        Map<Integer, ServerSocketChannel> activeListeners = new HashMap<>();

        try (Selector selector = Selector.open()) {
            while (running) {
                ConfigSnapshot config = configProvider.current();
                if (config.version() != knownVersion) {
                    knownVersion = config.version();
                    MDC.put("configVersion", String.valueOf(knownVersion));
                    reconcileListeners(selector, config, activeListeners);
                }

                selector.select(1000);
                cleanupExpiredStickyEntries(300);
                expireStaleConnects();
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
            for (ServerSocketChannel ch : activeListeners.values()) {
                try { ch.close(); } catch (IOException ignored) {}
            }
            MDC.clear();
        }
    }

    private void reconcileListeners(Selector selector, ConfigSnapshot config,
                                    Map<Integer, ServerSocketChannel> activeListeners) throws IOException {
        Set<Integer> desiredPorts = new HashSet<>();
        Map<Integer, String> portToPool = new HashMap<>();
        for (Listener l : config.listeners()) {
            desiredPorts.add(l.port());
            portToPool.put(l.port(), l.poolName());
        }
        
        var it = activeListeners.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!desiredPorts.contains(entry.getKey())) {
                MDC.put("pool", "removed");
                log.info("Closing listener on :{}", entry.getKey());
                MDC.remove("pool");
                entry.getValue().close();
                it.remove();
            }
        }

        for (Listener listener : config.listeners()) {
            if (activeListeners.containsKey(listener.port())) {
                SelectionKey key = activeListeners.get(listener.port()).keyFor(selector);
                if (key != null) {
                    key.attach(listener.poolName());
                }
            } else {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.bind(new InetSocketAddress(listener.port()));
                server.register(selector, SelectionKey.OP_ACCEPT, listener.poolName());
                activeListeners.put(listener.port(), server);
                MDC.put("pool", listener.poolName());
                log.info("Listening on :{}", listener.port());
                MDC.remove("pool");
            }
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
            SelectionKey connectKey = upstream.register(selector, SelectionKey.OP_CONNECT, pair);
            pendingConnects.put(connectKey, System.currentTimeMillis());

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
            Instant ts = stickyTimestamps.get(clientAddr);
            boolean expired = ts != null &&
                    Instant.now().isAfter(ts.plusSeconds(pool.stickyTtlSeconds()));

            if (sticky != null && !expired && healthy.contains(sticky)) {
                stickyTimestamps.put(clientAddr, Instant.now());
                return sticky;
            }
        }

        LoadBalancer lb = balancers.get(pool.algorithm());
        Upstream chosen = lb.pick(healthy, clientAddr);

        if (pool.stickyEnabled()) {
            stickyMap.put(clientAddr, chosen);
            stickyTimestamps.put(clientAddr, Instant.now());
        }
        return chosen;
    }

    private void cleanupExpiredStickyEntries(int defaultTtlSeconds) {
        Instant now = Instant.now();
        if (now.isBefore(lastStickyCleanup.plusSeconds(30))) return;
        lastStickyCleanup = now;

        stickyTimestamps.entrySet().removeIf(e ->
                now.isAfter(e.getValue().plusSeconds(defaultTtlSeconds)));

        stickyMap.keySet().retainAll(stickyTimestamps.keySet());
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel upstream = (SocketChannel) key.channel();
        ConnectionPair pair = (ConnectionPair) key.attachment();

        pendingConnects.remove(key);

        if (upstream.finishConnect()) {
            pair.upstreamKey = key;
            key.interestOps(SelectionKey.OP_READ);
            pair.clientKey = pair.clientChannel.register(key.selector(), SelectionKey.OP_READ, pair);

            MDC.put("pool", pair.poolName);
            MDC.put("upstream", pair.upstream.address());
            log.debug("Upstream connection established");
            MDC.remove("pool");
            MDC.remove("upstream");
        }
    }

    private void expireStaleConnects() {
        if (pendingConnects.isEmpty()) return;
        long now = System.currentTimeMillis();

        var it = pendingConnects.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue() > CONNECT_TIMEOUT_MS) {
                SelectionKey key = entry.getKey();
                it.remove();
                ConnectionPair pair = (ConnectionPair) key.attachment();
                MDC.put("pool", pair.poolName);
                MDC.put("upstream", pair.upstream.address());
                log.warn("Upstream connect timeout after {}ms", CONNECT_TIMEOUT_MS);
                MDC.remove("pool");
                MDC.remove("upstream");

                activeConnections.decrementAndGet();
                key.cancel();
                try { pair.upstreamChannel.close(); } catch (IOException ignored) {}
                try { pair.clientChannel.close(); } catch (IOException ignored) {}

                Counter.builder("lb_connections_total")
                        .tag("pool", pair.poolName).tag("status", "timeout")
                        .register(registry).increment();
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionPair pair = (ConnectionPair) key.attachment();

        boolean isClient = (channel == pair.clientChannel);
        var buffer = isClient ? pair.clientToUpstream : pair.upstreamToClient;
        SocketChannel targetChannel = isClient ? pair.upstreamChannel : pair.clientChannel;
        SelectionKey targetKey = isClient ? pair.upstreamKey : pair.clientKey;

        int read = channel.read(buffer);
        if (read == -1) {
            closeConnection(pair, key);
            return;
        }

        buffer.flip();
        int written = targetChannel.write(buffer);
        buffer.compact();

        // Back-pressure: buffer has remaining data that couldn't be written
        if (buffer.position() > 0) {
            // Stop reading from source until we drain the buffer
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            // Start listening for write-readiness on target
            if (targetKey != null && targetKey.isValid()) {
                targetKey.interestOps(targetKey.interestOps() | SelectionKey.OP_WRITE);
            }
        }

        Counter.builder("lb_bytes_total")
                .tag("pool", pair.poolName)
                .tag("direction", isClient ? "inbound" : "outbound")
                .register(registry).increment(read);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionPair pair = (ConnectionPair) key.attachment();

        boolean isUpstream = (channel == pair.upstreamChannel);
        // We're writing TO this channel, so use the buffer that flows toward it
        var buffer = isUpstream ? pair.clientToUpstream : pair.upstreamToClient;
        SelectionKey sourceKey = isUpstream ? pair.clientKey : pair.upstreamKey;

        buffer.flip();
        channel.write(buffer);
        buffer.compact();

        // Buffer fully drained — resume reading from source, stop write interest
        if (buffer.position() == 0) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            if (sourceKey != null && sourceKey.isValid()) {
                sourceKey.interestOps(sourceKey.interestOps() | SelectionKey.OP_READ);
            }
        }
    }

    private void closeConnection(ConnectionPair pair, SelectionKey key) {
        MDC.put("pool", pair.poolName);
        MDC.put("upstream", pair.upstream.address());

        activeConnections.decrementAndGet();
        try { pair.clientChannel.close(); } catch (IOException ignored) {}
        try { pair.upstreamChannel.close(); } catch (IOException ignored) {}
        key.cancel();

        ConfigSnapshot config = configProvider.current();
        Pool pool = config.pools().get(pair.poolName);
        if (pool != null) {
            LoadBalancer lb = balancers.get(pool.algorithm());
            if (lb instanceof LeastConnectionsBalancer lcb) {
                lcb.onDisconnect(pair.upstream);
            }
        }

        log.debug("Connection closed");
        MDC.remove("pool");
        MDC.remove("upstream");
    }
}