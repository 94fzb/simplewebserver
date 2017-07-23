package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.Controller;

public class Http2DemoController extends Controller {

    public static void main(String[] args) throws InterruptedException {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setSupportHttp2(true);
        serverConfig.setIsSsl(true);
        serverConfig.getRouter().addMapper("/", Http2DemoController.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }

    public void index() {
        helloWorld();
    }

    public void helloWorld() {
        getRequest().getCookies();
        getResponse().renderText("Hello world/v" + ServerInfo.getVersion());
    }
}
