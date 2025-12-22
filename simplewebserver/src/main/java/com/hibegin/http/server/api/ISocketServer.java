package com.hibegin.http.server.api;

public interface ISocketServer {

    void listen();

    default void destroy() {
        destroy(null);
    }

    void destroy(String reason);

    boolean create();

    boolean create(int port);

    boolean create(String hostname, int port);

    int getPort();
}
