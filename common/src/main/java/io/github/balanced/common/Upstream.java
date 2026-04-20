package io.github.balanced.common;

public record Upstream(
        long id,
        String host,
        int port,
        int weight
) {
    public String address() {
        return host + ":" + port;
    }
}