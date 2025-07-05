package com.hibegin.lambda;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.lambda.rest.LambdaApiGatewayRequest;
import com.hibegin.lambda.rest.LambdaApiGatewayResponse;

import java.util.Map;

public class LambdaApplication {

    static {
        initLambdaEnv();
    }

    public static void initLambdaEnv() {
        if (!EnvKit.isLambda()) {
            return;
        }
        System.getProperties().put("sws.log.path", "/tmp/log");
        System.getProperties().put("sws.temp.path", "/tmp/temp");
        System.getProperties().put("sws.cache.path", "/tmp/cache");
        System.getProperties().put("sws.static.path", "/tmp/static");
        System.getProperties().put("sws.conf.path", "/tmp/conf");
    }

    public static void startHandle(AbstractServerConfig serverConfig) throws Exception {
        LambdaEventIterator lambdaEventIterator = new LambdaEventIterator();
        LambdaHandler lambdaHandler = new LambdaHandler(serverConfig);
        //处理请求
        while (lambdaEventIterator.hasNext()) {
            Map.Entry<String, LambdaApiGatewayRequest> requestInfo = lambdaEventIterator.next();
            LambdaApiGatewayResponse apiGatewayResponse = lambdaHandler.doHandle(requestInfo.getValue());
            lambdaEventIterator.report(apiGatewayResponse, requestInfo.getKey());
        }
    }
}