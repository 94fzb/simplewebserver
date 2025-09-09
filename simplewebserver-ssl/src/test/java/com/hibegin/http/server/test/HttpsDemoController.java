package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.Controller;

public class HttpsDemoController extends Controller {

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setSupportHttp2(false);
        serverConfig.setSsl(true);
        serverConfig.getRouter().addMapper("/", HttpsDemoController.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }

    public void index() {
        getResponse().renderHtmlStr("Hello world/v" + ServerInfo.getVersion() + "_" + System.currentTimeMillis());
    }

}
