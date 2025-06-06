package com.hibegin.http.server;

import com.hibegin.common.io.handler.ReadWriteSelectorHandler;
import com.hibegin.common.io.handler.ReadWriteSelectorHandlerUtils;
import com.hibegin.common.io.handler.SslChannelFactory;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleHttpsWebServer extends SimpleWebServer {

    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleHttpsWebServer.class);

    private SSLContext sslContext;

    public SimpleHttpsWebServer(ServerConfig serverConfig, RequestConfig requestConfig, ResponseConfig responseConfig) {
        super(serverConfig, requestConfig, responseConfig);
        String password = ConfigKit.get("server.ssl.keystore.password", "").toString();
        String fileString = ConfigKit.get("server.ssl.keystore", null).toString();
        File file = null;
        if (fileString.startsWith("classpath:")) {
            try (InputStream inputStream = SimpleHttpsWebServer.class.getResourceAsStream(fileString.substring("classpath:".length()))) {
                if (Objects.nonNull(inputStream)) {
                    byte[] fileBytes = IOUtil.getByteByInputStream(inputStream);
                    file = File.createTempFile("keystore", fileString.substring(fileString.lastIndexOf(".")));
                    IOUtil.writeBytesToFile(fileBytes, file);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        } else {
            file = new File(fileString);
        }
        if (file == null || !file.exists()) {
            throw new RuntimeException("keystore can't null or not exists");
        } else {
            try {
                sslContext = SslChannelFactory.getSSLContext(file, password);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
    }

    @Override
    public ReadWriteSelectorHandler getReadWriteSelectorHandlerInstance(SocketChannel channel, SelectionKey key) throws IOException {
        return ReadWriteSelectorHandlerUtils.buildServerReadWriteSelectorHandler(channel, requestConfig.getRequestMaxBufferSize(), key, sslContext, false);
    }

    @Override
    public boolean create() {
        return super.create(ConfigKit.getHttpsServerPort());
    }
}
