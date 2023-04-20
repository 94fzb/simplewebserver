package com.hibegin.http.server.api;

public interface ISocketServer {

    void listener();

    void destroy();

    boolean create();

    boolean create(int port);
    boolean create(String hostname, int port);
}
