package com.hibegin.http.server.di;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Controller;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Explicit bindings contributed by application modules before the first application context is initialized.
 * A builder belongs to one application. build() freezes registrations and returns the same container thereafter.
 * Instances are never automatically closed; their owner remains responsible for lifecycle management.
 */
public final class SwsBindings {

    public interface Resolver {
        <T> T get(Class<T> type);
    }

    @FunctionalInterface
    public interface Provider<T> {
        T create(Resolver resolver) throws Exception;
    }

    @FunctionalInterface
    public interface ControllerProvider<T extends Controller> {
        T create(Resolver resolver, HttpRequest request, HttpResponse response) throws Exception;
    }

    private final Map<Class<?>, Provider<?>> providers = new LinkedHashMap<>();
    private final Map<Class<? extends Controller>, ControllerProvider<?>> controllers = new LinkedHashMap<>();
    private Container container;

    public synchronized <T> SwsBindings instance(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type").cast(Objects.requireNonNull(instance, "instance"));
        return singleton(type, resolver -> instance);
    }

    /** Creates one successful instance per application, lazily and safely under concurrent resolution. */
    public synchronized <T> SwsBindings singleton(Class<T> type, Provider<? extends T> provider) {
        requireUnbound(type);
        if (Controller.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Controllers must use controller() bindings: " + type.getName());
        }
        providers.put(type, Objects.requireNonNull(provider, "provider"));
        return this;
    }

    /** Registers a factory, not a controller instance. The factory must create a new controller each time. */
    public synchronized <T extends Controller> SwsBindings controller(Class<T> type, ControllerProvider<T> provider) {
        requireUnbound(type);
        controllers.put(type, Objects.requireNonNull(provider, "provider"));
        return this;
    }

    /** Interceptors registered this way share an application-scoped instance. */
    public <T extends Interceptor> SwsBindings interceptor(Class<T> type, Provider<? extends T> provider) {
        return singleton(type, provider);
    }

    private void requireUnbound(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (container != null) {
            throw new IllegalStateException("Application bindings are already built; register modules before initialization");
        }
        if (providers.containsKey(type) || controllers.containsKey(type)) {
            throw new IllegalArgumentException("Duplicate binding: " + type.getName());
        }
    }

    public synchronized Container build() {
        if (container == null) {
            container = new Container(providers, controllers);
        }
        return container;
    }

    /** Only factories and assembly code should resolve components; business objects receive constructor arguments. */
    public static final class Container implements ComponentFactory, Resolver {
        private final Map<Class<?>, Binding> bindings = new LinkedHashMap<>();
        private final Map<Class<? extends Controller>, ControllerProvider<?>> controllers;
        private final DefaultComponentFactory fallback = new DefaultComponentFactory();
        private final Object creationLock = new Object();
        private final ThreadLocal<Deque<Class<?>>> resolving = ThreadLocal.withInitial(ArrayDeque::new);

        private Container(Map<Class<?>, Provider<?>> providers,
                          Map<Class<? extends Controller>, ControllerProvider<?>> controllers) {
            providers.forEach((type, provider) -> bindings.put(type, new Binding(provider)));
            this.controllers = new LinkedHashMap<>(controllers);
        }

        @Override
        public <T> T get(Class<T> type) {
            Objects.requireNonNull(type, "type");
            Binding binding = bindings.get(type);
            if (binding == null) {
                String path = pathWith(type);
                clearEmptyPath();
                throw new CreationException("Missing binding: " + path);
            }
            if (binding.instance != null) {
                return type.cast(binding.instance);
            }
            // A single reentrant creation lock avoids lock-order deadlocks between dependent singletons.
            synchronized (creationLock) {
                if (binding.instance == null) {
                    enter(type);
                    try {
                        binding.instance = checked(type, binding.provider.create(this));
                    } catch (Exception e) {
                        throw failure(e);
                    } finally {
                        leave();
                    }
                }
                return type.cast(binding.instance);
            }
        }

        @Override
        public Controller createController(Class<? extends Controller> type, HttpRequest request, HttpResponse response)
                throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
            ControllerProvider<?> provider = controllers.get(type);
            if (provider == null) {
                return fallback.createController(type, request, response);
            }
            enter(type);
            try {
                return checked(type, provider.create(this, request, response));
            } catch (Exception e) {
                throw failure(e);
            } finally {
                leave();
            }
        }

        @Override
        public Interceptor createInterceptor(Class<? extends Interceptor> type)
                throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
            return bindings.containsKey(type) ? get(type) : fallback.createInterceptor(type);
        }

        private <T> T checked(Class<T> type, Object value) {
            if (!type.isInstance(value)) {
                throw new CreationException("Factory returned " + (value == null ? "null" : value.getClass().getName())
                        + " for " + pathWith(null));
            }
            return type.cast(value);
        }

        private void enter(Class<?> type) {
            Deque<Class<?>> path = resolving.get();
            if (path.contains(type)) {
                throw new CreationException("Circular dependency: " + pathWith(type));
            }
            path.addLast(type);
        }

        private void leave() {
            resolving.get().removeLast();
            clearEmptyPath();
        }

        private void clearEmptyPath() {
            if (resolving.get().isEmpty()) {
                resolving.remove();
            }
        }

        private String pathWith(Class<?> last) {
            String path = resolving.get().stream().map(Class::getName).collect(Collectors.joining(" -> "));
            return last == null ? path : path.isEmpty() ? last.getName() : path + " -> " + last.getName();
        }

        private CreationException failure(Exception cause) {
            if (cause instanceof CreationException) {
                return (CreationException) cause;
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new CreationException("Cannot create " + pathWith(null), cause);
        }

        private static final class Binding {
            private final Provider<?> provider;
            private volatile Object instance;

            private Binding(Provider<?> provider) {
                this.provider = provider;
            }
        }
    }

    public static final class CreationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private CreationException(String message) {
            super(message);
        }

        private CreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
