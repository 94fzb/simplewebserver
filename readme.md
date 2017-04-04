# SimpleWebServer

- SimpleWebServer基于NIO实现一个款轻量级的Web应用服务器，jar包仅100kb
- 具有，简易，灵活，更少的依赖，更多的扩展，更少的内存占用等特点
- 能快速搭建Web项目，可运行在嵌入式，Android设备上

## 功能

- 1.实现对HTTP请求的处理，可用于展示一些静态页面
- 2.支持文件上传，下载，cookie，json
- 3.路由请求配置
- 4.freemarker 模板
- 5.多线程支持
- 6.支持 https

## 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.hibegin</groupId>
    <artifactId>simplewebserver</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 快速启动一个Web服务
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

## Changelog

[完整的版本变化日志](CHANGELOG.md)

## 其他

* WebServer 默认端口为 `6058` 在 `conf/conf.properties` 中，或则通过代码的方式进行配置
* 使用 `FreeMarkerKit.init` 初始化模板文件根目录，`FreeMarkerKit.initClassTemplate` 初始模板相对jar中的根目录
* 服务器上时建议打包为 `jar` 文件运行（推荐使用 maven，jar文件路径与`conf` 文件夹同目录）
* 依赖的json和freemarker的jar都是非必须

## License

SimpleWebServer is Open Source software released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).