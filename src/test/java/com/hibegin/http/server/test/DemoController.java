package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.MethodInterceptor;

public class DemoController extends Controller{

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.getRouter().addMapper("", DemoController.class);
        serverConfig.addInterceptor(MethodInterceptor.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }

    public void index() {
        helloWorld();
    }

    public void helloWorld() {
        getResponse().renderText("Hello world/v" + ServerInfo.getVersion());
    }
}
