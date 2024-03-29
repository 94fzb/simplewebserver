package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.Controller;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 加载 resource 内的静态资源，http://localhost:6058/index.html
 */
public class StaticMapperController extends Controller {

    public void upload() {
        Map<String, Object> map = new HashMap<>();
        File file = getRequest().getFile("file");
        File newFile = new File(PathUtil.getTempPath() + "cp." + file.getName());
        file.renameTo(newFile);
        map.put("file", newFile.toString());
        map.put("fileSize", newFile.length());
        response.renderJson(map);
    }

    public void form() {
        response.renderJson(request.decodeParamMap());
    }

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.getRouter().addMapper("/api", StaticMapperController.class);
        serverConfig.addStaticResourceMapper("/", "/static");
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }
}
