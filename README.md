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
    <version>4.1.17</version>
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

## 上传文件

从 `4.1.17` 起，`request.getUploadedFile("file")` 返回 `UploadedFile`，提供表单字段名 `getFieldName()`、
客户端原始文件名 `getOriginalFilename()`、声明的类型 `getContentType()`、实际字节数 `getSize()`
和空文件判断 `isEmpty()`；字段不存在时返回 `null`。Web、Servlet 和 Lambda 使用相同接口。

```java
UploadedFile upload = request.getUploadedFile("file");
if (upload != null) {
    try (InputStream input = upload.getInputStream()) {
        // 在当前请求内读取内容，或保存到业务管理的持久存储。
    }
}
```

`upload.getFile()` 与原有的 `request.getFile("file")` 返回同一个临时文件；
每次 `getInputStream()` 都从该文件新建独立的文件流，由调用方关闭。
请求结束后临时文件会被清理，需要保留的内容应在请求结束前保存。
原始文件名和类型缺失时为 `null`；未选择文件时原始文件名可能为空字符串。
客户端文件名可能包含路径，只作为元信息使用，存储路径应由业务生成。
此接口沿用现有单文件上传解析能力，不增加运行时依赖。

## 手动依赖注入

使用 `serverConfig.getBindings()` 注册应用共享对象和 Controller 工厂，无需新增模块或引入 DI 框架。
下面的业务类型仅作示意，各模块可以在自己的启动配置（例如 `WebSetup`）中贡献注册：

```java
// 公共模块：整个应用只注册一次 CacheService
serverConfig.getBindings().singleton(CacheService.class, r -> new CacheServiceImpl());

// admin 模块：只负责自己的装配
serverConfig.getBindings()
    .singleton(AdminService.class, r -> new AdminService(r.get(CacheService.class)))
    .controller(AdminController.class, (r, request, response) ->
        new AdminController(r.get(AdminService.class)));
serverConfig.getRouter().addMapper("/admin", AdminController.class);

// blog 模块：获得与 admin 相同的 CacheService 实例
serverConfig.getBindings()
    .controller(BlogController.class, (r, request, response) ->
        new BlogController(r.get(CacheService.class)));
serverConfig.getRouter().addMapper("/blog", BlogController.class);
```

业务类通过普通构造器接收依赖；`r.get(...)` 留在装配工厂中。模块先完成注册，再初始化应用，
不必按依赖顺序注册。入口程序负责调用模块启动配置，不需要了解各个业务类。

- `instance(Type.class, object)` 注册已有实例；`singleton(Type.class, factory)` 在首次使用时创建，
  同一 `ServerConfig` 内只保留一个成功创建的实例，并发首次解析也不会重复创建。
- `controller(Type.class, factory)` 每次分派都会调用工厂，工厂必须返回新的 Controller。
  SWS 在构造完成后绑定 `request/response`；构造期间若需要请求，可使用工厂的请求参数。
- `interceptor(Type.class, factory)` 注册共享拦截器，另用 `addInterceptor(Type.class)` 指定执行顺序。
  共享服务和拦截器须支持并发使用，不应在字段里保存当前请求或用户。
- `ApplicationContext.init()`、首次创建 Controller 或显式 `build()` 会冻结注册。
  `build()` 后重复调用返回同一个容器。重复绑定、缺失依赖、循环依赖及工厂返回 `null` 会报错；
  已注册工厂抛错不会退回默认构造，失败创建不会被缓存，后续解析可以重试。
- 不同 `ServerConfig` 的容器独立；复用同一个配置的上下文共享注册实例。
  Lambda 热调用也会复用服务，不同进程或 FaaS 实例之间不共享内存缓存。
  对象的启动、关闭和资源释放仍由应用管理。

未注册的 Controller 保留原有构造规则：优先使用公开的 `(HttpRequest, HttpResponse)` 构造器，
否则使用公开无参构造器。未注册的拦截器仍使用无参构造器。
现有路由及 `Controller.buildController(...)` 调用也会使用该注入机制。

需要接入其他 DI 实现时，可用 `serverConfig.setComponentFactory(...)` 提供 `ComponentFactory`，
也可以继承 `DefaultComponentFactory` 仅覆盖需要的创建逻辑。
自定义工厂与内置 bindings 二选一，须在调用 `getBindings()` 或应用初始化之前设置。

## 重定向 URL 编码

从 `4.1.15` 起，`response.redirect(url)` 直接使用调用方提供的 URL，不再自动进行百分号编码。
业务代码应在组装 URL 时编码路径片段和查询参数值；已有的 `%HH` 编码会原样保留。
以 `/` 开头的 URL 仍会加上应用的 context path。`forward(uri)` 同样要求调用方提供已编码的 URI。
含原始回车或换行符的重定向地址会被拒绝。

```java
String message = URLEncoder.encode("请求处理失败，请稍后重试", "UTF-8");
response.redirect("/admin/500?message=" + message);
```

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
