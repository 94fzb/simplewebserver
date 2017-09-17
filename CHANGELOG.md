V0.2(2017-09-18)

* 优化
    * Cookie 的过期时间的设置方式
    * Json 序列化是使用 Gson 替换 flexjson
    * 对代理请求的处理
    * 支持 HTTP 请求创建，销毁的监听接口
    * HTTPS 配置更加方便

* 修复
    * 默认的日志存放路径不支持中文路径
    * Chrome 浏览器部分特殊文件无法进行下载
    * 一处内存泄漏
    * HttpSession中的Map不能使用 null
    * 几出已知的NullPointException


V0.1(2016-11-19)

* 变更Cookie的生成机制，及使用request.getSession()，后才添加用于标示会话的Cookie
* 添加请求超时的设置参数
* 修复staticMapper会暴露静态的列表
* 变更Interceptor为单例

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