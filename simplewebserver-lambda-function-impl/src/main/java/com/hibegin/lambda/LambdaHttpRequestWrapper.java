package com.hibegin.lambda;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.common.util.UrlDecodeUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.impl.SimpleHttpRequest;
import com.hibegin.http.server.util.HttpQueryStringUtils;
import com.hibegin.lambda.rest.LambdaApiGatewayRequest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

public class LambdaHttpRequestWrapper extends SimpleHttpRequest {

    private final LambdaApiGatewayRequest lambdaApiGatewayRequest;


    protected LambdaHttpRequestWrapper(ApplicationContext applicationContext, RequestConfig requestConfig, LambdaApiGatewayRequest lambdaApiGatewayRequest) {
        super(null, applicationContext, requestConfig);
        this.lambdaApiGatewayRequest = lambdaApiGatewayRequest;
        this.queryStr = ObjectUtil.requireNonNullElse(lambdaApiGatewayRequest.getRawQueryString(), "");
        this.method = HttpMethod.valueOf(lambdaApiGatewayRequest.getRequestContext().getHttp().getMethod());
        this.header = lambdaApiGatewayRequest.getHeaders();
        this.paramMap = HttpQueryStringUtils.parseUrlEncodedStrToMap(this.queryStr);
        this.getHeaderMap().put("Host", lambdaApiGatewayRequest.getRequestContext().getDomainName());
        this.uri = UrlDecodeUtils.decodePath(lambdaApiGatewayRequest.getRawPath().substring(getContextPath().length()), requestConfig.getCharSet());
        if (Objects.nonNull(lambdaApiGatewayRequest.getBody()) && !lambdaApiGatewayRequest.getBody().isEmpty()) {
            if (getLambdaApiGatewayRequest().isBase64Encoded()) {
                this.inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(lambdaApiGatewayRequest.getBody()));
            } else {
                this.inputStream = new ByteArrayInputStream(lambdaApiGatewayRequest.getBody().getBytes());
            }
        }
        //
        ServerConfig serverConfig = super.getServerConfig();
        serverConfig.setApplicationName("Lambda Function");
        serverConfig.setApplicationVersion(LambdaEventIterator.VERSION);
    }

    @Override
    public File getFile(String key) {
        if (Objects.isNull(files) || files.isEmpty()) {
            try {
                files = HttpRequestDecoderImpl.getFiles(getServerConfig(), IOUtil.getByteByInputStream(inputStream));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return super.getFile(key);
    }

    public LambdaApiGatewayRequest getLambdaApiGatewayRequest() {
        return lambdaApiGatewayRequest;
    }
}
