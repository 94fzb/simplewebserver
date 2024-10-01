## v0.3 (2024-10-x)

> Java21 loom + GraalVM Native 让应用有更小的内存占用和更快捷的运行体验

### 优化

- 调整最低的 java 运行版本为 java8
- 加入 GraalVM native image 启动 agent 的支持
- 增加的对外部资源的文件夹的快速访问
- 可以使用 `autoIndex` 模式渲染一个文件夹（类似 nginx）
- mimetype 支持更多的类型，添加 mp4/mp3 和 md 文件的支持
- 升级 gson,freemarker 依赖库
- IOUtils 对文件的读取私用 NIO 的方式
- 优化日志文件的存储
- 添加一个 @ResponseBody 注解，可以快速响应 gson
- 添加 LengthByteArrayInputStream 对于数据量不大的请 byte[] 可以获取到长度，避免使用 chunk stream 进行响应
- 添加 HandleAbleInterceptor 可以个做基础的前置 Interceptor 的过滤
- 添加 HttpErrorHandle 对全局对的未捕获异常的处理
- 添加 `BasicTemplate` 模版的支持，可以简单的实现模版的功能（仅提供变量替换的逻辑）
- gzip 开启后，还需要配置 gzipMimeTypes，避免对很多已经是压缩后了流进行二次压缩，带来的额外资源占用
- 提供配置程序的名称和版本信息
- 优化对 http stream header 的解析，降低对内存占用
- 可配置 header buffer, body buffer 的大小，header 默认为 128kb，body为 512kb，避免过多的临时文件缓存到磁盘
- disableCookie -> disableSession， web application 应该支持支持 cookie，而 session 通常是可选的
- request.getScheme() 添加对 `X-Forwarded-Proto` 的支持
- 完善 PathUtil， 支持配置更多的程序运行的各种目录
- 添加 499 错误码
- Cookie 支持设置 `sameSite`
- 对 Java21 的 Virtual Thread 的支持，request decode thread 和 handle thread pool 都可以替换为使用 virtual 的线程池
- 添加启动错误和成功回调方法

### 修复

- 脚步发布到 maven center 成功后出现的 500 错误
- 修复 Cookie 在 safari 里面无法持久化（ safari 严格要求 cooke 的 expires 必须为 GMT 格式的时间 ）
- 修复 GzipCompressingInputStream 在十分极端情况下的 read 卡死的情况


## v0.2 (2017-09-18)

### 优化
* Cookie 的过期时间的设置方式
* Json 序列化是使用 Gson 替换 flexjson
* 对代理请求的处理
* 支持 HTTP 请求创建，销毁的监听接口
* HTTPS 配置更加方便

#### 修复
* 默认的日志存放路径不支持中文路径
* Chrome 浏览器部分特殊文件无法进行下载
* 一处内存泄漏
* HttpSession中的Map不能使用 null
* 几出已知的NullPointException


## v0.1 (2016-11-19)

* 变更Cookie的生成机制，及使用request.getSession()，后才添加用于标示会话的Cookie
* 添加请求超时的设置参数
* 修复staticMapper会暴露静态的列表
* 变更Interceptor为单例

## v0.0.10 (2016-09-17)

* 添加到中央仓库
* 对部分包结构进行调整
* 添加对应的版本信息
* 修复几处异常
* 扩展对freemarker，添加了ClassLoaderTemplate的支持

## v0.0.3 (2015-12-13)

* 引入对 Https 的支持
* 变更创建 WebServer的方式
* 支持 Gzip 流的压缩
* 支持单进程启动多个 Server (Router,Interceptor 非 static)
* 增加对部分代理软件请求的支持
* 修复部分请求上传文件导致的异常

## v0.0.2 (2015-08-16)

* 添加freemarker
* 增加日志信息记录
* 处理Session多线程线程安全

## v0.0.1 (2015-02-24)

* 实现最为基础的静态文件的请求响应(第一次提交代码)