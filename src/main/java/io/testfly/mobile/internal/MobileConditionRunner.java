package io.testfly.mobile.internal;

import io.testfly.mobile.MobileConditions;
import io.testfly.mobile.MobileDevice;
import io.testfly.precondition.ConditionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs {@code @PreCondition} names of a mobile test with {@link MobileConditions} providers. When the
 * app keeps its state between tests, a condition that already ran on a device is not run again on
 * it; otherwise it runs before every test. A retry always runs it again.
 */
public final class MobileConditionRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileConditionRunner.class);
    private static final List<MobileConditions> PROVIDERS = new CopyOnWriteArrayList<>();
    /** Device id → conditions satisfied on it (only used when the app keeps its state). */
    private static final Map<String, Set<String>> SATISFIED = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private MobileConditionRunner() {
    }

    public static void register(MobileConditions provider) {
        PROVIDERS.add(provider);
    }

    /** Satisfies {@code names} on the current test's device, in order. */
    public static void run(String[] names, boolean isRetry) {
        loadAll();
        MobileDevice device = MobileSessions.current();
        Set<String> satisfied = SATISFIED.computeIfAbsent(device.id(), id -> ConcurrentHashMap.newKeySet());
        boolean keepsState = device.keepsAppState();
        for (String name : names) {
            if (isRetry || !keepsState) {
                satisfied.remove(name);
            }
            if (keepsState && satisfied.contains(name)) {
                LOGGER.info("[PreCondition] '{}' already satisfied on {}", name, device.id());
                continue;
            }
            LOGGER.info("[PreCondition] Running mobile provider for '{}' on {}", name, device.id());
            invoke(name);
            satisfied.add(name);
        }
    }

    /** Forgets every satisfied condition (suite end, tests). */
    public static void clearAll() {
        SATISFIED.clear();
    }

    private static void invoke(String name) {
        for (MobileConditions provider : PROVIDERS) {
            for (Method method : provider.getClass().getMethods()) {
                ConditionProvider annotation = method.getAnnotation(ConditionProvider.class);
                if (annotation != null && annotation.value().equals(name)) {
                    try {
                        method.invoke(provider);
                        return;
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        throw new IllegalStateException("[PreCondition] Mobile provider failed for '" + name + "': " + cause.getMessage(), cause);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("[PreCondition] Cannot call " + method + ": it must be public", e);
                    }
                }
            }
        }
        throw new IllegalStateException("[PreCondition] No mobile @ConditionProvider for '" + name + "'. A mobile test uses "
                + "MobileConditions subclasses (META-INF/services/io.testfly.mobile.MobileConditions or MobileConditions.register).");
    }

    private static void loadAll() {
        if (loaded) {
            return;
        }
        synchronized (PROVIDERS) {
            if (!loaded) {
                ServiceLoader.load(MobileConditions.class).forEach(PROVIDERS::add);
                loaded = true;
            }
        }
    }
}
