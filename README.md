# SimpleWebServer

[![Maven Central](https://img.shields.io/maven-central/v/com.hibegin/simplewebserver.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.hibegin/simplewebserver)

> SimpleWebServer 是一个使用 Java 基于 NIO 编写的超轻量级开源 Web 应用服务器。

是否遇到有时候想做一些小的 Web 程序，但是迫于 Java 运行环境过于繁琐而迟迟没有下手？那么现在除了 SpringBoot，广大的 Java 程序员又多了一个选择。

## 特点

### 轻量级

- 不基于 Servlet，源代码仅 3000 行左右。
- JAR 包大小仅 0.1 MB 左右。
- 零依赖，无 XML，极低的内存占用。
- 支持嵌入式设备（如树莓派）和 Android 平台。

### 完整

- 基本常用 API 都有，包括 Request、Response、Controller、Cookie/Session、Interceptor、Json、Freemarker 模板、文件上传、GZip 和静态文件服务器等。

### GraalVM Native

- 使用 Java 21 Loom + GraalVM Native，让应用有更小的内存占用和更快捷的运行体验。
- 在使用 Native 后，单个可执行文件大概 15 MB 左右，通过 zip 压缩后程序在 5 MB 左右，足够小（对比完整 JDK）。

### SimpleWebServer-Cli

- 快速体验：[SimpleWebServer-Cli](https://github.com/94fzb/simplewebserver-cli)
- 基于 SimpleWebServer，使用 GraalVM Native Image，提供一个简单的文件服务分享服务（无需 Java 环境）。
- 类似于 Python 的一行代码启动一个基于 HTTP 的文件服务，但比 Python 更简单，无需 pip。

## 快速上手

### Java 版本

- **必须**：Java 8 及以上版本。
- **推荐**：Java 11 及以上的版本。

### 添加依赖
```xml
<dependency>
    <groupId>com.hibegin</groupId>
    <artifactId>simplewebserver</artifactId>
    <version>4.0.107</version>
</dependency>
```
### 示例代码

```java
public class DemoController extends Controller {

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

然后在浏览器中输入 `http://localhost:6058` 即可访问。

## 打包

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

运行 `mvn clean compile assembly:single` 进行打包。

## 性能

简单与号称“性能打爆网卡的 tio”对比，感兴趣移步到 [SimpleWebServer 性能测试](https://gitee.com/94fzb/simplewebserver-performance)。

## Changelog

[完整的版本变化日志](CHANGELOG.md)

## TODO

- 支持 HTTP 2.0 基本协议（不包含服务端推送）
- 提供类似 SpringMVC 通过注解完成 Restful API 的编写

## 其他

- WebServer 默认端口为 `6058`，可以在 `conf/conf.properties` 中配置，或通过代码的方式进行配置。
- 使用 `FreeMarkerKit.init` 初始化模板文件根目录，`FreeMarkerKit.initClassTemplate` 初始化模板相对 JAR 中的根目录。
- 服务器上建议打包为 `JAR` 文件运行（推荐使用 Maven，JAR 文件路径与 `conf` 文件夹同目录）。
- 依赖的 JSON 和 Freemarker 的 JAR 都是非必须的。

## License

SimpleWebServer 是 Open Source 软件，发布在 [Apache 2.0 许可证](https://www.apache.org/licenses/LICENSE-2.0.html) 下。
