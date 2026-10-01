package io.testfly.examples.api;

import com.sun.net.httpserver.HttpServer;
import io.testfly.client.ApiClient;
import io.testfly.internal.api.ApiExecution;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

/** Informational localhost benchmark; run main manually, no CI timing threshold. */
public final class ApiTransportBenchmark {
    private ApiTransportBenchmark() {}
    public static void main(String[] args) throws Exception {
        int count = args.length == 0 ? 100 : Integer.parseInt(args[0]);
        if (count < 1) throw new IllegalArgumentException("Positive call count required");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        Set<Integer> connections = java.util.concurrent.ConcurrentHashMap.newKeySet();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            connections.add(exchange.getRemoteAddress().getPort());
            exchange.sendResponseHeaders(200, 2); exchange.getResponseBody().write("ok".getBytes()); exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            ApiClient.get(url).send(); connections.clear();
            long[] latency = new long[count]; long start = System.nanoTime();
            for (int i = 0; i < count; i++) { long before = System.nanoTime(); ApiClient.get(url).send().assertStatus(200); latency[i] = System.nanoTime() - before; }
            double seconds = (System.nanoTime() - start) / 1e9; Arrays.sort(latency);
            System.out.printf(Locale.ROOT, "calls=%d throughput=%.2f/s p50=%.2fms p95=%.2fms observedTCPConnections=%d%n",
                count, count / seconds, latency[(count - 1) / 2] / 1e6, latency[(int)Math.ceil(count * .95) - 1] / 1e6, connections.size());
        } finally { ApiExecution.shutdown(); server.stop(0); executor.shutdownNow(); }
    }
}
