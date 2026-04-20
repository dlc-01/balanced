package io.github.balanced.dataplane;

import io.github.balanced.common.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DataPlaneE2ETest {

    private ServerSocket upstream1;
    private ServerSocket upstream2;
    private DataPlane dataPlane;
    private Thread dpThread;
    private int lbPort;

    private final CopyOnWriteArrayList<String> upstream1Hits = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> upstream2Hits = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // Start two mock upstreams that echo back their name
        upstream1 = new ServerSocket(0);
        upstream2 = new ServerSocket(0);

        startEchoServer(upstream1, "upstream1", upstream1Hits);
        startEchoServer(upstream2, "upstream2", upstream2Hits);

        // Find a free port for the LB
        try (ServerSocket tmp = new ServerSocket(0)) {
            lbPort = tmp.getLocalPort();
        }

        ConfigSnapshot config = new ConfigSnapshot(
                List.of(new Listener(lbPort, "test")),
                Map.of("test", new Pool("test", BalancingAlgorithm.ROUND_ROBIN, false, 0,
                        List.of(
                                new Upstream(1, "127.0.0.1", upstream1.getLocalPort(), 1),
                                new Upstream(2, "127.0.0.1", upstream2.getLocalPort(), 1)
                        ))),
                1
        );

        AtomicReference<ConfigSnapshot> ref = new AtomicReference<>(config);
        dataPlane = new DataPlane(ref::get, new SimpleMeterRegistry());
        dpThread = new Thread(dataPlane, "dp-test");
        dpThread.setDaemon(true);
        dpThread.start();

        // Give the event loop time to bind
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() throws Exception {
        dataPlane.shutdown();
        dpThread.join(2000);
        upstream1.close();
        upstream2.close();
    }

    @Test
    void roundRobinDistributesAcrossUpstreams() throws Exception {
        for (int i = 0; i < 4; i++) {
            String response = sendThroughLb("ping" + i);
            assertThat(response).startsWith("echo:");
        }

        // Give echo servers time to register hits
        Thread.sleep(100);

        assertThat(upstream1Hits.size()).isEqualTo(2);
        assertThat(upstream2Hits.size()).isEqualTo(2);
    }

    @Test
    void dataFlowsBidirectionally() throws Exception {
        String response = sendThroughLb("hello");
        assertThat(response).isEqualTo("echo:hello");
    }

    private String sendThroughLb(String message) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", lbPort)) {
            socket.setSoTimeout(2000);
            OutputStream out = socket.getOutputStream();
            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Read response
            InputStream in = socket.getInputStream();
            byte[] buf = new byte[1024];
            int read = in.read(buf);
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        }
    }

    private void startEchoServer(ServerSocket server, String name, List<String> hits) {
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    Thread handler = new Thread(() -> {
                        try {
                            InputStream in = client.getInputStream();
                            byte[] buf = new byte[1024];
                            int read = in.read(buf);
                            if (read > 0) {
                                String msg = new String(buf, 0, read, StandardCharsets.UTF_8);
                                hits.add(msg);
                                OutputStream out = client.getOutputStream();
                                out.write(("echo:" + msg).getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            }
                            client.close();
                        } catch (IOException ignored) {}
                    });
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException ignored) {}
            }
        }, name + "-acceptor");
        t.setDaemon(true);
        t.start();
    }
}
