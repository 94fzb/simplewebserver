package com.hibegin.http.server;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.handler.SSLChannelFactory;
import com.hibegin.http.server.handler.SSLReadWriteSelectorHandler;
import com.hibegin.http.server.util.PathUtil;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleHttpsWebServer extends SimpleWebServer {

    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpsWebServer.class);

    private SSLContext sslContext;

    public SimpleHttpsWebServer(ServerConfig serverConfig, RequestConfig requestConfig, ResponseConfig responseConfig) {
        super(serverConfig, requestConfig, responseConfig);
        String password = ConfigKit.get("server.ssl.keystore.password", "").toString();
        File file = new File(PathUtil.getConfFile(ConfigKit.get("server.ssl.keystore", null).toString()));
        try {
            sslContext = SSLChannelFactory.getSSLContext(file, password);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    @Override
    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel, SelectionKey key) throws IOException {
        return new SSLReadWriteSelectorHandler(channel, key, sslContext);
    }

    @Override
    public boolean create() {
        return super.create(ConfigKit.getHttpsServerPort());
    }
}
