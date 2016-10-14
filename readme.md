#基于NIO的web服务器
------------

> 简易，灵活，更少的依赖，更多的扩展。更少的内存占用，能快速搭建Web项目。可快速运行在嵌入式, Android 设备上

------------
##应用场景


##基本功能

- 1.实现对浏览器请求的处理，可以展示一些静态页面
- 2.支持文件上传，下载，cookie，json
- 3.路由请求配置
- 4.freemarker 模板
- 5.多线程支持
- 6.支持 https

##快速创建一个WebServer示例

```java
package com.hibegin.http.server.test;

import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.ServerInfo;
import com.hibegin.http.server.web.Controller;

public class DemoController extends Controller{

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.getRouter().addMapper("", DemoController.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }

    public void index() {
        helloWorld();
    }

    public void helloWorld() {
        getResponse().renderText("Hello world/v" + ServerInfo.getVersion());
    }
}
```

然后浏览器输入 http://localhost:6058

##Changelog

V0.0.10(2016-09-17)

* 添加到中央仓库
* 对部分包结构进行调整
* 添加对应的版本信息
* 修复几处异常
* 扩展对freemarker，添加了ClassLoaderTemplate的支持

V0.0.3(2015-12-13)

* 引入对 Https 的支持
* 变更创建 WebServer的方式
* 支持 Gzip 流的压缩
* 支持单进程启动多个 Server (Router,Interceptor 非 static)
* 增加对部分代理软件请求的支持
* 修复部分请求上传文件导致的异常

V0.0.2(2015-08-16)

* 添加freemarker
* 增加日志信息记录
* 处理Session多线程线程安全

V0.0.1(2015-02-24)

* 实现最为基础的静态文件的请求响应(第一次提交代码)

##Maven依赖

```xml
<dependency>
    <groupId>com.hibegin</groupId>
    <artifactId>simplewebserver</artifactId>
    <version>0.0.10</version>
</dependency>
```

##其他

* WebServer 默认端口为 `6058` 在 `conf/conf.properties` 中，或则通过代码的方式进行配置
* 使用 `FreeMarkerKit.init` 初始化模板文件根目录，`FreeMarkerKit.initClassTemplate` 初始模板相对jar中的根目录
* 服务器上时建议打包为 `jar` 文件运行（推荐使用 maven，jar文件路径与`conf` 文件夹同目录）
* 依赖的json和freemarker的jar都是非必须