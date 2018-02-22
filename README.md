﻿# SimpleWebServer

> SimpleWebServer 是一款使用Java基于NIO编写的超轻量级开源Web Application Server

是否遇到有时候想做一些小的Web程序，但是迫于Java运行环境过于繁琐而迟迟没有下手，那么现在除了SpringBoot，广大的Java程序员又多了一个选择

### 轻量级
并不基于servlet，源代码仅3000行左右，jar包仅 0.1m 左右，零依赖，无xml，极低的内存占用，所以不用担心程序能不能在嵌入式（树莓派）/Android 上能否正常运行

### 完整
Cookie，Interceptor，Json，模板，文件上传，基本常用API都有，使得写Web应用更容易

### 快速上手
request, response, controller. interceptor 还是熟悉的配方，熟悉的味道

```xml
<dependency>
    <groupId>com.hibegin</groupId>
    <artifactId>simplewebserver</artifactId>
    <version>0.2.10</version>
</dependency>
```

```java
public class DemoController extends Controller{

    public static void main(String[] args) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.getRouter().addMapper("", DemoController.class);
        new WebServerBuilder.Builder().serverConfig(serverConfig).build().startWithThread();
    }

    public void index() {
          getResponse().renderText("Hello world/v" + ServerInfo.getVersion());
    }
}
```

然后浏览器输入 http://localhost:6058

### 打包

**推荐使用 maven-assembly-plugin**

```xml
<build>
    <plugins>
        <plugin>
            <artifactId>maven-assembly-plugin</artifactId>
            <version>2.5.5</version>
            <configuration>
                <archive>
                    <manifest>
                        <mainClass>com.hibegin.http.server.test.DemoController</mainClass>
                    </manifest>
                </archive>
                <finalName>simplewebserver-demo</finalName>
                <descriptorRefs>
                    <descriptorRef>jar-with-dependencies</descriptorRef>
                </descriptorRefs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

`mvn clean compile assembly:single`

### 性能
简单与号称 “性能打爆网卡的tio” 对比，感兴趣移步到 https://gitee.com/94fzb/simplewebserver-performance

## Changelog

[完整的版本变化日志](CHANGELOG.md)

## TODO

- 支持HTTP2.0基本协议（不包含服务端推送）
- ~~实现多线程解码HTTP请求~~
- 提供类似 SpringMVC 通过注解完成 Restful API的编写
- 提供多种 JSON 序列化工具包支持
- 提供HTTP错误码错误页面配置功能

## 其他

* WebServer 默认端口为 `6058` 在 `conf/conf.properties` 中，或则通过代码的方式进行配置
* 使用 `FreeMarkerKit.init` 初始化模板文件根目录，`FreeMarkerKit.initClassTemplate` 初始模板相对jar中的根目录
* 服务器上时建议打包为 `jar` 文件运行（推荐使用 maven，jar文件路径与`conf` 文件夹同目录）
* 依赖的json和freemarker的jar都是非必须

## License

SimpleWebServer is Open Source software released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).
