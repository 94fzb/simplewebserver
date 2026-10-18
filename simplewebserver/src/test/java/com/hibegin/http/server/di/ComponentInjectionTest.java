package com.hibegin.http.server.di;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.MethodInterceptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ComponentInjectionTest {

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
    public void routedAndDirectDispatchUseFreshControllersWithSharedServices() throws Exception {
        ServerConfig config = config();
        List<CachedController> created = new ArrayList<>();
        config.getBindings().controller(CachedController.class, (r, req, res) -> {
            CachedController controller = new CachedController(r.get(Cache.class));
            created.add(controller);
            return controller;
        });
        HttpRequest firstRequest = request(config, "/cache");
        AtomicReference<String> body = new AtomicReference<>();
        HttpResponse firstResponse = response(body);
        new MethodInterceptor().doInterceptor(firstRequest, firstResponse);
        assertEquals("/cache:1", body.get());

        HttpRequest secondRequest = request(config, "/cache-next");
        HttpResponse secondResponse = response(body);
        CachedController second = (CachedController) Controller.buildController(
                CachedController.class.getMethod("index"), secondRequest, secondResponse);
        second.index();
        assertEquals("/cache-next:2", body.get());
        assertEquals(2, created.size());
        CachedController first = created.get(0);
        assertNotSame(first, second);
        assertSame(first.cache, second.cache);
        assertSame(firstRequest, first.getRequest());
        assertSame(firstResponse, first.getResponse());
        assertSame(secondRequest, second.getRequest());
        assertSame(secondResponse, second.getResponse());
    }

    @Test
    public void concurrentDispatchKeepsEachRequestAndResponseSeparate() throws Exception {
        ServerConfig config = config();
        CountDownLatch controllersReady = new CountDownLatch(8);
        config.getBindings().controller(CachedController.class, (r, req, res) -> {
            CachedController controller = new CachedController(r.get(Cache.class));
            controllersReady.countDown();
            if (!controllersReady.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Controller creation was serialized");
            }
            return controller;
        });
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                String uri = "/cache-" + i;
                results.add(executor.submit(() -> {
                    AtomicReference<String> body = new AtomicReference<>();
                    new MethodInterceptor().doInterceptor(request(config, uri), response(body));
                    assertTrue(body.get().startsWith(uri + ":"));
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
            assertEquals(8, config.getBindings().build().get(Cache.class).calls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void separateServerConfigsHaveSeparateServices() {
        ServerConfig first = config();
        ServerConfig second = config();
        assertNotSame(first.getBindings().build().get(Cache.class), second.getBindings().build().get(Cache.class));
    }

    @Test
    public void injectedInterceptorsKeepOrderAndShareServicesAcrossContextsUsingTheSameConfig() {
        ServerConfig config = config();
        config.addInterceptor(CachedInterceptor.class);
        config.addInterceptor(LegacyInterceptor.class);
        config.addInterceptor(CachedInterceptor.class);
        config.getBindings().interceptor(CachedInterceptor.class, r -> new CachedInterceptor(r.get(Cache.class)));
        ApplicationContext first = new ApplicationContext(config);
        first.init();
        first.init();
        ApplicationContext second = new ApplicationContext(config);
        second.init();
        assertEquals(2, first.getInterceptors().size());
        assertTrue(first.getInterceptors().get(0) instanceof CachedInterceptor);
        assertTrue(first.getInterceptors().get(1) instanceof LegacyInterceptor);
        assertSame(first.getInterceptors().get(0), second.getInterceptors().get(0));
        assertNotSame(first.getInterceptors().get(1), second.getInterceptors().get(1));
        assertSame(config.getBindings().build().get(Cache.class),
                ((CachedInterceptor) first.getInterceptors().get(0)).cache);
        assertThrows(IllegalStateException.class, () -> config.getBindings().instance(String.class, "late"));
    }

    @Test
    public void interceptorFactoryFailureDoesNotSilentlySkipItOrMarkContextInitialized() {
        ServerConfig config = config();
        AtomicInteger attempts = new AtomicInteger();
        config.addInterceptor(LegacyInterceptor.class);
        config.getBindings().interceptor(LegacyInterceptor.class, r -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("not ready");
            }
            return new LegacyInterceptor();
        });
        ApplicationContext context = new ApplicationContext(config);
        assertThrows(SwsBindings.CreationException.class, context::init);
        assertNull(context.getInterceptors());
        context.init();
        assertEquals(1, context.getInterceptors().size());
        assertEquals(2, attempts.get());
    }

    @Test
    public void legacyConstructorsWorkAndRequestResponseConstructorTakesPriority() throws Exception {
        HttpRequest request = request(new ServerConfig(), "/legacy");
        HttpResponse response = response(new AtomicReference<>());
        Controller noArg = Controller.buildController(NoArgController.class.getMethod("index"), request, response);
        assertSame(request, noArg.getRequest());
        assertSame(response, noArg.getResponse());
        RequestController withRequest = (RequestController) Controller.buildController(
                RequestController.class.getMethod("index"), request, response);
        assertSame(request, withRequest.constructorRequest);
        assertSame(response, withRequest.getResponse());
        Controller withoutConfig = Controller.buildController(NoArgController.class.getMethod("index"),
                request(null, "/legacy"), response);
        assertNotNull(withoutConfig.getRequest());
    }

    @Test
    public void registeredControllerFactoryFailureNeverFallsBackToItsDefaultConstructor() {
        ServerConfig config = new ServerConfig();
        config.getBindings().controller(NoArgController.class, (r, req, res) -> { throw new IOException("broken"); });
        SwsBindings.CreationException error = assertThrows(SwsBindings.CreationException.class,
                () -> Controller.buildController(NoArgController.class.getMethod("index"), request(config, "/"), null));
        assertEquals("broken", error.getCause().getMessage());
    }

    @Test
    public void applicationInitFreezesBindingsEvenWithoutInterceptors() {
        ServerConfig config = new ServerConfig();
        config.setPort(18080);
        new ApplicationContext(config).init();
        assertThrows(IllegalStateException.class, () -> config.getBindings().instance(String.class, "late"));
    }

    @Test
    public void customFactoryIsAnAlternativeToBuiltInBindingsAndReceivesTheRequest() throws Exception {
        ServerConfig config = new ServerConfig();
        Cache cache = new Cache();
        HttpRequest request = request(config, "/custom");
        HttpResponse response = response(new AtomicReference<>());
        config.setComponentFactory(new DefaultComponentFactory() {
            @Override
            public Controller createController(Class<? extends Controller> type, HttpRequest req, HttpResponse res) {
                assertSame(request, req);
                assertSame(response, res);
                return new CachedController(cache);
            }
        });
        CachedController controller = (CachedController) Controller.buildController(
                CachedController.class.getMethod("index"), request, response);
        assertSame(cache, controller.cache);
        assertSame(request, controller.getRequest());
        assertThrows(IllegalStateException.class, config::getBindings);
        assertThrows(IllegalStateException.class, () -> config.setComponentFactory(new DefaultComponentFactory()));
        ServerConfig builtIn = new ServerConfig();
        builtIn.getBindings();
        assertThrows(IllegalStateException.class, () -> builtIn.setComponentFactory(new DefaultComponentFactory()));
    }

    private ServerConfig config() {
        ServerConfig config = new ServerConfig();
        config.setPort(18080);
        config.getBindings().singleton(Cache.class, r -> new Cache());
        config.getRouter().addMapper("/cache", CachedController.class);
        return config;
    }

    private static HttpRequest request(ServerConfig config, String uri) {
        RequestConfig requestConfig = new RequestConfig();
        if (config != null) {
            requestConfig.setRouter(config.getRouter());
        }
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getContextPath": return "";
                        case "getUri": return uri;
                        case "getMethod": return HttpMethod.GET;
                        case "getServerConfig": return config;
                        case "getRequestConfig": return requestConfig;
                        default: throw new AssertionError("Unexpected request call: " + method.getName());
                    }
                });
    }

    private static HttpResponse response(AtomicReference<String> body) {
        return (HttpResponse) Proxy.newProxyInstance(HttpResponse.class.getClassLoader(),
                new Class[]{HttpResponse.class}, (proxy, method, args) -> {
                    if (method.getName().equals("renderText")) {
                        body.set((String) args[0]);
                        return null;
                    }
                    throw new AssertionError("Unexpected response call: " + method.getName());
                });
    }

    private static class Cache {
        private final AtomicInteger calls = new AtomicInteger();
    }

    public static class CachedController extends Controller {
        private final Cache cache;

        private CachedController(Cache cache) {
            this.cache = cache;
        }

        public void index() {
            response.renderText(request.getUri() + ":" + cache.calls.incrementAndGet());
        }
    }

    public static class NoArgController extends Controller {
        public void index() { }
    }

    public static class RequestController extends Controller {
        private final HttpRequest constructorRequest;

        public RequestController() {
            throw new AssertionError("Must prefer the request/response constructor");
        }

        public RequestController(HttpRequest request, HttpResponse response) {
            super(request, response);
            constructorRequest = request;
        }

        public void index() { }
    }

    public static class CachedInterceptor implements Interceptor {
        private final Cache cache;

        private CachedInterceptor(Cache cache) {
            this.cache = cache;
        }

        @Override
        public boolean doInterceptor(HttpRequest request, HttpResponse response) {
            return true;
        }
    }

    public static class LegacyInterceptor implements Interceptor {
        @Override
        public boolean doInterceptor(HttpRequest request, HttpResponse response) {
            return true;
        }
    }
}
