package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.Controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DemoController extends Controller {

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        //serverConfig.setPort(0);
        //serverConfig.setPort(0);
        serverConfig.getRouter().addMapper("/", DemoController.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }

    public void index() {
        //System.out.println("Thread.currentThread() = " + Thread.currentThread().getName());
        helloWorld();
    }

    public void helloWorld() {
        getResponse().renderHtmlStr("Hello world/v" + ServerInfo.getVersion() + "_" + System.currentTimeMillis());
    }

    public void json() {
        Map<String, Object> map = new HashMap<>();
        map.put("version", ServerInfo.getVersion());
        response.renderJson(map);
    }

    public void bigFile() throws IOException {
        File file = new File(PathUtil.getTempPath() + "/big_file.bin");
        try (FileOutputStream byteArrayOutputStream = new FileOutputStream(file)) {
            for (int i = 0; i < 1024 * 1024; i++) {
                byteArrayOutputStream.write(String.valueOf(i).getBytes());
            }
            response.renderFile(file);
        }
    }
}
