package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import io.testfly.client.UseAuth;
import io.testfly.config.TestFlyConfig.Api.AuthStrategy;
import io.testfly.listeners.TestExecutionListener;
import io.testfly.test.NoBrowser;
import org.testng.ITestClass;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/** Drives {@link TestExecutionListener#onTestStart} to prove {@code @UseAuth} applies YAML strategies. */
@Test(singleThreaded = true)
public class UseAuthListenerTest extends ApiClientTestBase {

    /**
     * Not a BaseApiTest subclass on purpose: TestNG would pick up its {@code @Listeners} and run the
     * real listener for this unit test's own methods. {@code @NoBrowser} gives the same no-driver path.
     */
    @UseAuth("classDefault")
    @NoBrowser
    public static class AnnotatedApiTest {
        @UseAuth("admin")
        public void adminCall() {
        }

        public void defaultCall() {
        }
    }

    private final AtomicReference<String> seen = new AtomicReference<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ITestResult result(String methodName) throws Exception {
        Method method = AnnotatedApiTest.class.getMethod(methodName);
        org.testng.internal.ConstructorOrMethod com = mock(org.testng.internal.ConstructorOrMethod.class);
        when(com.getMethod()).thenReturn(method);
        ITestNGMethod ngMethod = mock(ITestNGMethod.class);
        when(ngMethod.getConstructorOrMethod()).thenReturn(com);
        when(ngMethod.getMethodName()).thenReturn(methodName);
        when(ngMethod.getQualifiedName()).thenReturn(AnnotatedApiTest.class.getName() + "." + methodName);
        ITestClass testClass = mock(ITestClass.class);
        when(testClass.getRealClass()).thenReturn((Class) AnnotatedApiTest.class);
        ITestContext context = mock(ITestContext.class, RETURNS_DEEP_STUBS);
        ITestResult result = mock(ITestResult.class);
        when(result.getMethod()).thenReturn(ngMethod);
        when(result.getTestClass()).thenReturn(testClass);
        when(result.getTestContext()).thenReturn(context);
        return result;
    }

    private void configure() {
        AuthStrategy admin = new AuthStrategy();
        admin.setType("bearer");
        admin.setToken("admin-token");
        AuthStrategy classDefault = new AuthStrategy();
        classDefault.setType("apiKey");
        classDefault.setApiKey("class-key");
        config.getApi().setAuth(new java.util.LinkedHashMap<>(Map.of("admin", admin, "classDefault", classDefault)));
    }

    @Test
    public void methodAnnotationWinsOverClassAnnotation() throws Exception {
        configure();
        String base = server(ex -> {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            seen.set(auth != null ? auth : "key:" + ex.getRequestHeaders().getFirst("X-Api-Key"));
            reply(ex, 200, "ok");
        });
        TestExecutionListener listener = new TestExecutionListener();

        listener.onTestStart(result("adminCall"));
        ApiClient.get(base + "/a").send();
        assertEquals(seen.get(), "Bearer admin-token");

        io.testfly.internal.api.ApiExecution.cleanupTestContext();
        listener.onTestStart(result("defaultCall"));
        ApiClient.get(base + "/b").send();
        assertEquals(seen.get(), "key:class-key");
    }

    @Test
    public void unknownStrategyNameFailsTheTestStart() throws Exception {
        config.getApi().setAuth(new java.util.LinkedHashMap<>());
        IllegalStateException error = expectThrows(IllegalStateException.class,
                () -> new TestExecutionListener().onTestStart(result("adminCall")));
        assertTrue(error.getMessage().contains("admin"), error.getMessage());
    }
}
