package io.testfly.internal.api;

import io.testfly.client.*;
import io.testfly.internal.TestFlyContext;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/** Internal chain and virtual-thread lifecycle; not part of the stable API. */
public final class ApiExecution {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<java.util.function.BooleanSupplier> CANCELLED = new ThreadLocal<>();
    private static final Set<Scope> SCOPES = ConcurrentHashMap.newKeySet();
    private static ExecutorService executor;
    static { Runtime.getRuntime().addShutdownHook(new Thread(ApiExecution::shutdown, "testfly-api-shutdown")); }
    private ApiExecution() {}

    public static final class Scope {
        private boolean closed;
        private final Set<CompletableFuture<?>> pending = new HashSet<>();
        private Scope() { SCOPES.add(this); }
        public synchronized void log(Runnable action) { if (!closed) action.run(); }
        public synchronized boolean isOpen() { return !closed; }
        private synchronized void register(CompletableFuture<?> future) {
            if (closed) future.cancel(true);
            else pending.add(future);
        }
        private synchronized void remove(CompletableFuture<?> future) { pending.remove(future); }
        private void close() {
            List<CompletableFuture<?>> calls;
            synchronized (this) {
                closed = true;
                calls = List.copyOf(pending);
                pending.clear();
            }
            SCOPES.remove(this);
            // Future callbacks can execute application code: never invoke them under the scope monitor.
            for (CompletableFuture<?> future : calls) future.cancel(true);
        }
    }

    public static Scope scope() {
        Scope scope = CURRENT.get();
        if (scope == null) { scope = new Scope(); CURRENT.set(scope); }
        return scope;
    }
    public static void logInContext(Runnable action) {
        if (CANCELLED.get() != null && CANCELLED.get().getAsBoolean()) return;
        Scope scope = CURRENT.get();
        if (scope == null) action.run();
        else scope.log(action);
    }
    public static boolean contextOpen() { return CURRENT.get() == null || CURRENT.get().isOpen(); }
    public static void closeScope() {
        Scope scope = CURRENT.get();
        if (scope != null) scope.close();
        CURRENT.remove();
    }
    public static void cleanupTestContext() {
        closeScope();
        ApiClient.clearChainInterceptors();
        ApiClient.clearGlobalAuth();
        ApiClient.clearCookies();
    }
    public static synchronized void shutdown() {
        for (Scope scope : List.copyOf(SCOPES)) scope.close();
        CURRENT.remove();
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }
    private static synchronized ExecutorService executor() {
        if (executor == null) executor = Executors.newVirtualThreadPerTaskExecutor();
        return executor;
    }

    public static <T> CompletableFuture<T> submit(Scope scope, Callable<T> call) {
        String testId = TestFlyContext.getCurrentTestId();
        Class<?> testClass = TestFlyContext.getCurrentTestClass();
        java.lang.reflect.Method testMethod = TestFlyContext.getCurrentTestMethod();
        class CallFuture extends CompletableFuture<T> {
            FutureTask<Void> task;
            volatile boolean cancelRequested;
            @Override public boolean cancel(boolean interrupt) {
                if (isDone()) return isCancelled();
                cancelRequested = true;
                // Interrupt before completing the future: its callbacks may wait for the worker to stop.
                task.cancel(interrupt);
                return super.cancel(interrupt);
            }
        }
        CallFuture result = new CallFuture();
        result.task = new FutureTask<>(() -> {
            CURRENT.set(scope);
            CANCELLED.set(() -> result.cancelRequested || result.isCancelled());
            TestFlyContext.setCurrentTestId(testId);
            TestFlyContext.setCurrentTest(testClass, testMethod);
            try {
                check(scope);
                result.complete(call.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                TestFlyContext.clearCurrentTest();
                CURRENT.remove();
                CANCELLED.remove();
            }
            return null;
        });
        scope.register(result);
        result.whenComplete((value, error) -> scope.remove(result));
        if (!result.isDone()) {
            try { executor().execute(result.task); }
            catch (RejectedExecutionException error) { result.completeExceptionally(error); }
        }
        return result;
    }

    public static void check(Scope scope) {
        if (Thread.currentThread().isInterrupted() || !scope.isOpen()
                || (CANCELLED.get() != null && CANCELLED.get().getAsBoolean())) throw new CancellationException("API call cancelled");
    }

    public static void delay(long millis, Scope scope) {
        long remaining = TimeUnit.MILLISECONDS.toNanos(millis);
        long start = System.nanoTime();
        while (remaining > 0) {
            check(scope);
            LockSupport.parkNanos(remaining);
            remaining = TimeUnit.MILLISECONDS.toNanos(millis) - (System.nanoTime() - start);
        }
        check(scope);
    }

    public static ApiResponse invoke(List<ApiInterceptor> interceptors, ApiRequest request,
                                     Function<ApiRequest, ApiResponse> transport, Scope scope) {
        return invokeAt(interceptors, 0, request, transport, scope);
    }
    private static ApiResponse invokeAt(List<ApiInterceptor> list, int index, ApiRequest request,
                                       Function<ApiRequest, ApiResponse> transport, Scope scope) {
        check(scope);
        if (index == list.size()) return Objects.requireNonNull(transport.apply(request), "transport response");
        class Link implements ApiInterceptor.Chain {
            private final Thread owner = Thread.currentThread();
            private volatile boolean active = true;
            private void validate() {
                if (!active || Thread.currentThread() != owner)
                    throw new IllegalStateException("Chain is only valid during intercept() on its owner thread");
                check(scope);
            }
            public ApiRequest request() { validate(); return request; }
            public ApiResponse proceed(ApiRequest next) {
                validate();
                return invokeAt(list, index + 1, Objects.requireNonNull(next, "request"), transport, scope);
            }
        }
        Link link = new Link();
        try { return Objects.requireNonNull(list.get(index).intercept(link), "interceptor response"); }
        finally { link.active = false; }
    }

    /** Only this subtype represents retryable transport failures. */
    public static final class TransportFailure extends ApiException {
        public TransportFailure(ApiRequest request, java.io.IOException cause) {
            super(request.method(), request.uri().toString(), cause);
        }
    }
}
