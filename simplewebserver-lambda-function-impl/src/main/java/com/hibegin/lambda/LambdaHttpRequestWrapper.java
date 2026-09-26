package com.hibegin.lambda;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.common.util.UrlDecodeUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.SimpleHttpRequest;
import com.hibegin.http.server.util.HttpQueryStringUtils;
import com.hibegin.lambda.rest.LambdaApiGatewayRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class LambdaHttpRequestWrapper extends SimpleHttpRequest {

    protected LambdaHttpRequestWrapper(ApplicationContext applicationContext, RequestConfig requestConfig, LambdaApiGatewayRequest lambdaApiGatewayRequest) {
        super(null, applicationContext, requestConfig);
        this.createTime = System.currentTimeMillis();
        this.queryStr = ObjectUtil.requireNonNullElse(lambdaApiGatewayRequest.getRawQueryString(), "");
        this.method = HttpMethod.valueOf(lambdaApiGatewayRequest.getRequestContext().getHttp().getMethod());
        this.header = lambdaApiGatewayRequest.getHeaders();
        this.paramMap = HttpQueryStringUtils.parseUrlEncodedStrToMap(this.queryStr);
        this.getHeaderMap().put("Host", lambdaApiGatewayRequest.getRequestContext().getDomainName());
        this.uri = UrlDecodeUtils.decodePath(lambdaApiGatewayRequest.getRawPath().substring(getContextPath().length()), requestConfig.getCharSet());
        String body = lambdaApiGatewayRequest.getBody();
        if (body != null && !body.isEmpty()) {
            byte[] bytes = lambdaApiGatewayRequest.isBase64Encoded()
                    ? Base64.getDecoder().decode(body) : body.getBytes(StandardCharsets.UTF_8);
            try {
                appendRequestBody(bytes);
                decodeRequestBody();
            } catch (IOException e) {
                deleteTempUploadFiles();
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                deleteTempUploadFiles();
                throw e;
            }
        }
        ServerConfig serverConfig = super.getServerConfig();
        serverConfig.setApplicationName("Lambda " + (EnvKit.isLambdaResponseStreamEnabled() ? "Response Stream" : "Buffered"));
        serverConfig.setApplicationVersion(LambdaEventIterator.VERSION);
    }
}
