package com.hibegin.http.server;

import com.hibegin.http.server.config.ServerConfig;
import org.junit.After;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class SimpleWebServerPortReuseTest {

    private SimpleWebServer oldServer;
    private SimpleWebServer newServer;
    private Thread oldListenerThread;

    @After
    public void cleanup() throws InterruptedException {
        if (oldServer != null) {
            oldServer.destroy("test cleanup");
        }
        if (newServer != null) {
            newServer.destroy("test cleanup");
        }
        if (oldListenerThread != null) {
            oldListenerThread.join(2000);
        }
    }

    @Test
    public void portReuseIsDisabledByDefault() {
        assertFalse(new ServerConfig().isReusePort());
    }

    @Test
    public void replacementListenerCanBindWhileAcceptedConnectionRemainsOpen() throws Exception {
        assumeTrue(supportsReusePort());
        ServerConfig oldConfig = serverConfig();
        oldServer = new SimpleWebServer(oldConfig, null, null);
        assertTrue(oldServer.create("127.0.0.1", 0));
        int port = oldServer.getPort();
        oldListenerThread = new Thread(oldServer::listen, "old-http-listener-test");
        oldListenerThread.start();

        try (SocketChannel client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port))) {
            SocketChannel accepted = waitForAcceptedConnection(oldServer);
            oldServer.stopAccepting("test handoff");

            newServer = new SimpleWebServer(serverConfig().setPort(port), null, null);
            assertTrue(newServer.create("127.0.0.1", port));
            assertTrue(accepted.isOpen());
            assertTrue(accepted.keyFor(oldServer.selector).isValid());
        }
    }

    private ServerConfig serverConfig() {
        return new ServerConfig()
                .setApplicationName("PortReuseTest")
                .setDisablePrintWebServerInfo(true)
                .setDisableSavePidFile(true)
                .setReusePort(true);
    }

    private SocketChannel waitForAcceptedConnection(SimpleWebServer server) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            for (java.nio.channels.SelectionKey key : server.selector.keys()) {
                if (key.channel() instanceof SocketChannel) {
                    return (SocketChannel) key.channel();
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("HTTP connection was not accepted");
    }

    private boolean supportsReusePort() throws Exception {
        try (ServerSocketChannel channel = ServerSocketChannel.open()) {
            for (SocketOption<?> option : channel.supportedOptions()) {
                if ("SO_REUSEPORT".equals(option.name())) {
                    return true;
                }
            }
            return false;
        }
    }
}
