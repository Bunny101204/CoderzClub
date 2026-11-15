package com.coderzclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Redis auto-configuration/connectivity proof without Docker or a shared
 * Redis instance. The ephemeral server implements only PING/PONG and proves
 * Lettuce connects to the configured host/port rather than localhost:6379 or
 * an ambient production endpoint.
 */
class StagingRedisConnectivityTest {
    @Test
    void apiProfileUsesCanonicalSpringDataRedisEndpoint() throws Exception {
        assertProfileConnects("api");
    }

    @Test
    void workerProfileUsesCanonicalSpringDataRedisEndpoint() throws Exception {
        assertProfileConnects("worker");
    }

    private void assertProfileConnects(String profile) throws Exception {
        try (EphemeralRedis redis = new EphemeralRedis()) {
            new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withPropertyValues(
                    "spring.profiles.active=" + profile,
                    "spring.data.redis.host=127.0.0.1",
                    "spring.data.redis.port=" + redis.port(),
                    "spring.data.redis.database=7",
                    "spring.data.redis.repositories.enabled=false")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null,
                        () -> "Redis auto-configuration failed for " + profile + ": "
                            + context.getStartupFailure());
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertEquals("127.0.0.1", factory.getHostName());
                    assertEquals(redis.port(), factory.getPort());
                    assertEquals("PONG", factory.getConnection().ping());
                    assertTrue(redis.receivedPing(), "test Redis endpoint did not receive PING");
                });
        }
    }

    private static final class EphemeralRedis implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "staging-redis-test-server");
            thread.setDaemon(true);
            return thread;
        });
        private volatile boolean receivedPing;

        private EphemeralRedis() throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    serve(socket.getInputStream(), socket.getOutputStream());
                } catch (IOException ignored) {
                    // Shutdown closes the socket after the assertion completes.
                }
            });
        }

        private void serve(InputStream input, OutputStream output) throws IOException {
            while (true) {
                if (input.read() != '*') return;
                int argumentCount = Integer.parseInt(readLine(input));
                String command = "";
                for (int i = 0; i < argumentCount; i++) {
                    if (input.read() != '$') return;
                    int length = Integer.parseInt(readLine(input));
                    byte[] value = input.readNBytes(length);
                    input.read();
                    input.read();
                    if (i == 0) command = new String(value, StandardCharsets.UTF_8).toUpperCase();
                }
                switch (command) {
                    case "HELLO" -> output.write("-ERR unknown command 'HELLO'\r\n".getBytes());
                    case "PING" -> {
                        receivedPing = true;
                        output.write("+PONG\r\n".getBytes());
                    }
                    case "AUTH", "SELECT" -> output.write("+OK\r\n".getBytes());
                    default -> output.write("+OK\r\n".getBytes());
                }
                output.flush();
            }
        }

        private static String readLine(InputStream input) throws IOException {
            StringBuilder line = new StringBuilder();
            int previous = -1;
            int current;
            while ((current = input.read()) != -1) {
                if (previous == '\r' && current == '\n') {
                    return line.substring(0, line.length() - 1);
                }
                line.append((char) current);
                previous = current;
            }
            throw new IOException("Redis test server reached EOF");
        }

        private int port() {
            return server.getLocalPort();
        }

        private boolean receivedPing() {
            return receivedPing;
        }

        @Override
        public void close() throws IOException {
            server.close();
            executor.shutdownNow();
        }
    }
}
