module hibegin.simplewebserver.ssl {
    requires java.logging;
    requires hibegin.simplewebserver;
    exports com.hibegin.common.io.handler.ssl;
    exports com.hibegin.http.server.ssl;
}