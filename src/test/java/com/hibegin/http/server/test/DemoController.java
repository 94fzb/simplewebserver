package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.Controller;

import java.util.HashMap;
import java.util.Map;

public class DemoController extends Controller {

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.getRouter().addMapper("/", DemoController.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }

    public void index() {
        helloWorld();
    }

    public void helloWorld() {
        getResponse().renderText("Hello world/v" + ServerInfo.getVersion() + System.currentTimeMillis());
    }

    public void json() {
        Map<String, Object> map = new HashMap<>();
        map.put("version", ServerInfo.getVersion());
        response.renderJson(map);
    }
}
