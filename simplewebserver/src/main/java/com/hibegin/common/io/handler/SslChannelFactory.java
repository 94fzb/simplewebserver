package com.hibegin.common.io.handler;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

public class SslChannelFactory {

    public static SSLContext getSSLContext(File file, String password) throws Exception {
        char[] passphrase = password.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(file), passphrase);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    public static SSLContext createClientSSLContext() throws Exception {
        // 使用默认的系统 TrustStore（通常是 JDK cacerts）
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null); // 使用默认信任库

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null); // null 表示不使用客户端证书
        return sslContext;
    }
}
