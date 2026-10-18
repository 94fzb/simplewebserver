package com.hibegin.lambda;

import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.MethodInterceptor;
import com.hibegin.lambda.rest.ApiGatewayHttp;
import com.hibegin.lambda.rest.ApiGatewayRequestContext;
import com.hibegin.lambda.rest.LambdaApiGatewayRequest;
import com.hibegin.lambda.rest.LambdaApiGatewayResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LambdaInjectionTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private String oldTempPath;

    @Before
    public void setUp() {
        oldTempPath = System.getProperty("sws.temp.path");
        System.setProperty("sws.temp.path", temporaryFolder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() {
        if (oldTempPath == null) {
            System.clearProperty("sws.temp.path");
        } else {
            System.setProperty("sws.temp.path", oldTempPath);
        }
    }

    @Test
    public void warmInvocationsShareServicesButGetFreshControllersAndRequests() {
        ServerConfig server = new ServerConfig();
        server.setPort(18080);
        server.addInterceptor(MethodInterceptor.class);
        server.getRouter().addMapper("/counter", CounterController.class);
        List<CounterController> created = new ArrayList<>();
        server.getBindings().singleton(AtomicInteger.class, r -> new AtomicInteger());
        server.getBindings().controller(CounterController.class, (r, req, res) -> {
            CounterController controller = new CounterController(r.get(AtomicInteger.class));
            created.add(controller);
            return controller;
        });
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(server.getRouter());
        LambdaHandler handler = new LambdaHandler(new AbstractServerConfig() {
            @Override
            public ServerConfig getServerConfig() { return server; }
            @Override
            public RequestConfig getRequestConfig() { return requestConfig; }
            @Override
            public ResponseConfig getResponseConfig() { return new ResponseConfig(); }
        });

        LambdaApiGatewayResponse first = handler.doHandle(event("/counter-first"));
        LambdaApiGatewayResponse second = handler.doHandle(event("/counter-second"));
        assertEquals(Integer.valueOf(200), first.getStatusCode());
        assertEquals(Integer.valueOf(200), second.getStatusCode());
        assertEquals("/counter-first:1", first.getBody());
        assertEquals("/counter-second:2", second.getBody());
        assertEquals(2, created.size());
        assertNotSame(created.get(0), created.get(1));
        assertSame(created.get(0).counter, created.get(1).counter);
        assertNotSame(created.get(0).getRequest(), created.get(1).getRequest());
        assertNotSame(created.get(0).getResponse(), created.get(1).getResponse());
    }

    private LambdaApiGatewayRequest event(String path) {
        LambdaApiGatewayRequest event = new LambdaApiGatewayRequest();
        event.setRawPath(path);
        event.setHeaders(new HashMap<>());
        ApiGatewayHttp http = new ApiGatewayHttp();
        http.setMethod("GET");
        ApiGatewayRequestContext context = new ApiGatewayRequestContext();
        context.setHttp(http);
        context.setDomainName("example.com");
        event.setRequestContext(context);
        return event;
    }

    public static class CounterController extends Controller {
        private final AtomicInteger counter;

        private CounterController(AtomicInteger counter) {
            this.counter = counter;
        }

        public void index() {
            response.addHeader("Content-Type", "text/plain; charset=UTF-8");
            response.renderText(request.getUri() + ":" + counter.incrementAndGet());
        }
    }
}
