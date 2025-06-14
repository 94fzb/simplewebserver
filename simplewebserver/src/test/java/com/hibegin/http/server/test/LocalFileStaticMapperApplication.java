package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;

/**
 * 加载静态资源文件夹
 */
public class LocalFileStaticMapperApplication {


    public static void main(String[] args) {
        //InputStream inputStream = new LocalFileStaticResourceLoader(true, "/" + System.currentTimeMillis(), PathUtil.getRootPath()).getInputStream(PathUtil.getRootPath());
        //System.out.println("IOUtil.getStringInputStream(inputStream) = " + IOUtil.getStringInputStream(inputStream));
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.addLocalFileStaticResourceMapper("/sf", "/home/xiaochun/git", true);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }
}
