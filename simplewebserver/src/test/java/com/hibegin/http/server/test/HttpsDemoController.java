package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;

public class HttpsDemoController extends DemoController {

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setSupportHttp2(false);
        serverConfig.setSsl(true);
        serverConfig.getRouter().addMapper("/", DemoController.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }
}
