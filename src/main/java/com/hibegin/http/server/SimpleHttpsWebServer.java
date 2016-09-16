package com.hibegin.http.server;

import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.handler.SSLChannelFactory;
import com.hibegin.http.server.handler.SSLReadWriteSelectorHandler;
import com.hibegin.http.server.util.*;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class SimpleHttpsWebServer extends SimpleWebServer {

    private SSLContext sslContext;

    public SimpleHttpsWebServer() {
        this(null, null, null);
    }

    public SimpleHttpsWebServer(ServerConfig serverConfig, RequestConfig requestConfig, ResponseConfig responseConfig) {
        super(serverConfig, requestConfig, responseConfig);
        String password = ConfigKit.get("server.ssl.keystore.password", "").toString();
        File file = new File(PathUtil.getConfFile(ConfigKit.get("server.ssl.keystore", null).toString()));
        try {
            sslContext = SSLChannelFactory.getSSLContext(file, password);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel, SelectionKey key) throws IOException {
        return new SSLReadWriteSelectorHandler(channel, key, false, sslContext);
    }

    @Override
    public void create() {
        try {
            super.create(ConfigKit.getHttpsServerPort());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
