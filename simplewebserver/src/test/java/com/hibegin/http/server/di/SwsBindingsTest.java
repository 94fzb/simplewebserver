package com.hibegin.http.server.di;

import com.hibegin.http.server.web.Controller;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SwsBindingsTest {

    @Test
    public void modulesCanRegisterBeforeTheirSharedCacheAndObserveTheSameState() {
        SwsBindings bindings = new SwsBindings();
        bindings.singleton(AdminService.class, r -> new AdminService(r.get(Cache.class)));
        bindings.singleton(BlogService.class, r -> new BlogService(r.get(Cache.class)));
        AtomicInteger creations = new AtomicInteger();
        bindings.singleton(Cache.class, r -> {
            creations.incrementAndGet();
            return new Cache();
        });

        SwsBindings.Container container = bindings.build();
        assertEquals(0, creations.get());
        AdminService admin = container.get(AdminService.class);
        BlogService blog = container.get(BlogService.class);
        admin.cache.version.incrementAndGet();
        assertEquals(1, blog.cache.version.get());
        assertSame(admin.cache, blog.cache);
        assertEquals(1, creations.get());
        assertSame(container, bindings.build());
    }

    @Test
    public void concurrentFirstResolutionCreatesOneInstance() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        SwsBindings.Container container = new SwsBindings().singleton(Cache.class, r -> {
            creations.incrementAndGet();
            return new Cache();
        }).singleton(AdminService.class, r -> new AdminService(r.get(Cache.class))).build();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Cache>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                final boolean throughService = i % 2 == 0;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return throughService ? container.get(AdminService.class).cache : container.get(Cache.class);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Cache cache = results.get(0).get(5, TimeUnit.SECONDS);
            for (Future<Cache> result : results) {
                assertSame(cache, result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, creations.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void suppliedInstancesAreReusedAndBindingsCannotBeOverwrittenOrAddedAfterBuild() {
        Cache cache = new Cache();
        SwsBindings bindings = new SwsBindings().instance(Cache.class, cache);
        assertThrows(IllegalArgumentException.class, () -> bindings.singleton(Cache.class, r -> new Cache()));
        SwsBindings.Container container = bindings.build();
        assertSame(cache, container.get(Cache.class));
        assertThrows(IllegalStateException.class, () -> bindings.instance(String.class, "late"));
        assertThrows(IllegalArgumentException.class,
                () -> new SwsBindings().instance(Controller.class, new Controller()));
    }

    @Test
    public void missingAndCircularDependenciesReportTheirPathsWithoutPoisoningLaterResolution() {
        SwsBindings.Container missing = new SwsBindings()
                .singleton(AdminService.class, r -> new AdminService(r.get(Cache.class))).build();
        SwsBindings.CreationException missingError = assertThrows(SwsBindings.CreationException.class,
                () -> missing.get(AdminService.class));
        assertEquals("Missing binding: " + AdminService.class.getName() + " -> " + Cache.class.getName(),
                missingError.getMessage());
        assertEquals("Missing binding: " + Cache.class.getName(),
                assertThrows(SwsBindings.CreationException.class, () -> missing.get(Cache.class)).getMessage());

        SwsBindings.Container circular = new SwsBindings()
                .singleton(AdminService.class, r -> new AdminService(r.get(Cache.class)))
                .singleton(Cache.class, r -> r.get(AdminService.class).cache)
                .instance(String.class, "unrelated").build();
        assertEquals("Circular dependency: " + AdminService.class.getName() + " -> "
                        + Cache.class.getName() + " -> " + AdminService.class.getName(),
                assertThrows(SwsBindings.CreationException.class,
                        () -> circular.get(AdminService.class)).getMessage());
        assertEquals("unrelated", circular.get(String.class));
    }

    @Test
    public void failedCreationCanRetryAndRetainsTheOriginalCause() {
        AtomicInteger attempts = new AtomicInteger();
        IOException original = new IOException("not ready");
        SwsBindings.Container container = new SwsBindings().singleton(Cache.class, r -> {
            if (attempts.incrementAndGet() == 1) {
                throw original;
            }
            return new Cache();
        }).build();
        assertSame(original, assertThrows(SwsBindings.CreationException.class,
                () -> container.get(Cache.class)).getCause());
        Cache cache = container.get(Cache.class);
        assertSame(cache, container.get(Cache.class));
        assertEquals(2, attempts.get());
    }

    @Test
    public void nullResultsAndInterruptedProvidersFailExplicitly() {
        SwsBindings.Container container = new SwsBindings()
                .singleton(Cache.class, r -> null)
                .singleton(String.class, r -> { throw new InterruptedException("interrupted"); }).build();
        assertTrue(assertThrows(SwsBindings.CreationException.class,
                () -> container.get(Cache.class)).getMessage().contains("Factory returned null"));
        try {
            assertThrows(SwsBindings.CreationException.class, () -> container.get(String.class));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static class Cache {
        private final AtomicInteger version = new AtomicInteger();
    }

    private static class AdminService {
        private final Cache cache;

        private AdminService(Cache cache) {
            this.cache = cache;
        }
    }

    private static class BlogService {
        private final Cache cache;

        private BlogService(Cache cache) {
            this.cache = cache;
        }
    }
}
