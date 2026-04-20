package io.github.balanced.dataplane;

import io.github.balanced.common.Upstream;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

final class ConnectionPair {

    private static final int BUFFER_SIZE = 8192;

    final SocketChannel clientChannel;
    final SocketChannel upstreamChannel;
    final Upstream upstream;
    final String poolName;

    final ByteBuffer clientToUpstream = ByteBuffer.allocate(BUFFER_SIZE);
    final ByteBuffer upstreamToClient = ByteBuffer.allocate(BUFFER_SIZE);

    SelectionKey clientKey;
    SelectionKey upstreamKey;

    ConnectionPair(SocketChannel clientChannel, SocketChannel upstreamChannel,
                   Upstream upstream, String poolName) {
        this.clientChannel = clientChannel;
        this.upstreamChannel = upstreamChannel;
        this.upstream = upstream;
        this.poolName = poolName;
    }
}