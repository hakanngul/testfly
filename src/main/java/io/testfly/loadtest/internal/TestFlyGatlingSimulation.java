package io.testfly.loadtest.internal;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import io.testfly.loadtest.LoadStep;

import java.time.Duration;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling Simulation subclass that reads its configuration from
 * {@link GatlingRunConfig} — set by {@link GatlingEngine} before
 * {@code Gatling.fromMap()} is invoked.
 *
 * <p>
 * This class is only loaded when Gatling is on the classpath
 * (guarded by {@link GatlingBridge#isAvailable()}).
 */
public class TestFlyGatlingSimulation extends Simulation {

    {
        GatlingRunConfig cfg = GatlingRunConfig.get();
        if (cfg == null) {
            String configFile = System.getProperty("testfly.gatling.config");
            if (configFile != null && new java.io.File(configFile).exists()) {
                try {
                    cfg = GatlingRunConfig.load(new java.io.File(configFile));
                } catch (Exception e) {
                    throw new IllegalStateException("[LoadTest] Failed to load config from " + configFile, e);
                }
            }
        }
        if (cfg == null) {
            throw new IllegalStateException(
                    "[LoadTest] GatlingRunConfig not set. This simulation must be " +
                            "launched via GatlingEngine, not directly.");
        }

        // ── HTTP Protocol ──
        HttpProtocolBuilder httpProtocol = http
                .baseUrl(cfg.baseUrl)
                .acceptHeader("application/json")
                .userAgentHeader("TestFly-LoadTest/1.0");

        // ── Build step chain ──
        ChainBuilder stepsChain = buildStepsChain(cfg);

        // ── Scenario with hold-duration loop ──
        ScenarioBuilder scn = scenario(cfg.scenarioName)
                .during(Duration.ofSeconds(cfg.holdSeconds))
                .on(stepsChain);

        // ── Injection: ramp users over rampUp duration ──
        setUp(
                scn.injectOpen(
                        rampUsers(cfg.users).during(Duration.ofSeconds(cfg.rampUpSeconds))))
                .protocols(httpProtocol);
    }

    private static ChainBuilder buildStepsChain(GatlingRunConfig cfg) {
        ChainBuilder chain = null;

        for (GatlingRunConfig.GatlingStep step : cfg.steps) {
            HttpRequestActionBuilder req = buildRequest(step);

            if (chain == null) {
                chain = exec(req);
            } else {
                chain = chain.exec(req);
            }

            // Think time between steps
            if (cfg.thinkTimeFixedMs > 0) {
                chain = chain.pause(Duration.ofMillis(cfg.thinkTimeFixedMs));
            } else if (cfg.thinkTimeMinMs >= 0 && cfg.thinkTimeMaxMs >= 0) {
                chain = chain.pause(
                        Duration.ofMillis(cfg.thinkTimeMinMs),
                        Duration.ofMillis(cfg.thinkTimeMaxMs));
            }
        }

        if (chain == null) {
            chain = exec(http("empty").get("/"));
        }

        return chain;
    }

    /**
     * Converts a {@link GatlingRunConfig.GatlingStep} into a Gatling
     * {@link HttpRequestActionBuilder}.
     */
    private static HttpRequestActionBuilder buildRequest(GatlingRunConfig.GatlingStep step) {
        String path = toGatlingEl(step.path);

        // Build base request with method + path
        HttpRequestActionBuilder req = switch (step.method != null ? step.method.toUpperCase() : "GET") {
            case "POST" -> http(step.name).post(path);
            case "PUT" -> http(step.name).put(path);
            case "DELETE" -> http(step.name).delete(path);
            case "PATCH" -> http(step.name).patch(path);
            default -> http(step.name).get(path);
        };

        // Headers
        for (Map.Entry<String, String> h : step.headers.entrySet()) {
            req = req.header(h.getKey(), toGatlingEl(h.getValue()));
        }

        // Query params
        for (Map.Entry<String, Object> qp : step.queryParams.entrySet()) {
            req = req.queryParam(qp.getKey(), String.valueOf(qp.getValue()));
        }

        // Body
        if (step.body != null) {
            req = req.body(StringBody(toGatlingEl(step.body))).asJson();
        }

        // Checks (explicit or Gatling default)
        for (Integer expectedCode : step.statusChecks) {
            req = req.check(status().is(expectedCode));
        }
        for (String jsonPathExpr : step.jsonPathChecks) {
            req = req.check(jsonPath(toGatlingEl(jsonPathExpr)).exists());
        }

        // Extractions via jsonPath().saveAs()
        for (Map.Entry<String, String> ext : step.extractions.entrySet()) {
            req = req.check(jsonPath(toGatlingEl(ext.getValue())).saveAs(ext.getKey()));
        }

        return req;
    }

    /**
     * Converts TestFly's {@code ${var}} syntax to Gatling's EL syntax
     * {@code #{var}}.
     */
    private static String toGatlingEl(String value) {
        if (value == null)
            return null;
        return value.replace("${", "#{");
    }

    /**
     * Converts a Map body to a JSON string with Gatling EL expressions.
     */
    private static String mapToJson(Object body) {
        if (!(body instanceof Map<?, ?> map)) {
            return body.toString();
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first)
                sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String sv) {
                sb.append("\"").append(toGatlingEl(sv)).append("\"");
            } else if (v instanceof Map) {
                sb.append(mapToJson(v));
            } else {
                sb.append(v);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
