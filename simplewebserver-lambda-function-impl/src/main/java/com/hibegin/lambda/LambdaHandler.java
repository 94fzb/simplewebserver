package com.hibegin.lambda;

import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.handler.HttpRequestHandlerRunnable;
import com.hibegin.lambda.rest.LambdaApiGatewayRequest;
import com.hibegin.lambda.rest.LambdaApiGatewayResponse;

import java.net.http.HttpClient;

public class LambdaHandler {


    private final ApplicationContext applicationContext;
    private final AbstractServerConfig serverConfig;

    public LambdaHandler(AbstractServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        applicationContext = new ApplicationContext(this.serverConfig.getServerConfig());
        applicationContext.init();
    }

    /**
     * 普通（非流式）处理，完整缓冲后一次性返回
     */
    public LambdaApiGatewayResponse doHandle(LambdaApiGatewayRequest lambdaApiGatewayRequest) {
        HttpRequest request = new LambdaHttpRequestWrapper(applicationContext, serverConfig.getRequestConfig(), lambdaApiGatewayRequest);
        LambdaHttpResponseWrapper lambdaHttpResponseWrapper = new LambdaHttpResponseWrapper(request, serverConfig.getResponseConfig());
        new HttpRequestHandlerRunnable(request, lambdaHttpResponseWrapper).run();
        return lambdaHttpResponseWrapper.getOutput();
    }

    /**
     * 流式处理：直接通过 Lambda Runtime API 的 streaming 模式发送响应，
     * 不再需要 LambdaEventIterator.report() 上报。
     *
     * @param lambdaApiGatewayRequest 请求对象
     * @param requestId               Lambda 请求 ID
     * @param httpClient              共享的 HttpClient 实例
     * @return true 表示已通过流式方式发送，调用方应跳过 report；false 表示未使用流式
     */
    public boolean doStreamingHandle(LambdaApiGatewayRequest lambdaApiGatewayRequest, String requestId, HttpClient httpClient) {
        HttpRequest request = new LambdaHttpRequestWrapper(applicationContext, serverConfig.getRequestConfig(), lambdaApiGatewayRequest);
        LambdaStreamingHttpResponseWrapper streamingResponse = new LambdaStreamingHttpResponseWrapper(
                request, serverConfig.getResponseConfig(), requestId, httpClient);
        new HttpRequestHandlerRunnable(request, streamingResponse).run();
        return streamingResponse.isStreamingSent();
    }
}

