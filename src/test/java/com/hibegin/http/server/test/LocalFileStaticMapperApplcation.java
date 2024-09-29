package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;

/**
 * 加载静态资源文件夹
 */
public class LocalFileStaticMapperApplcation {


    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.addLocalFileStaticResourceMapper("/sf", "/home/xiaochun/git", true);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }
}
