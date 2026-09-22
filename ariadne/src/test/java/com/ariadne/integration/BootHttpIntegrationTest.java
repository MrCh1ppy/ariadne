package com.ariadne.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "ARIADNE_INTEGRATION_HTTP", matches = "true")
class BootHttpIntegrationTest {
    private static final AtomicInteger catalogueCalls = new AtomicInteger();
    private static final AtomicInteger navCalls = new AtomicInteger();
    private static final HttpServer python = fakePython();

    @LocalServerPort int port;

    private static HttpServer fakePython() {
        try {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/funds", exchange -> respond(exchange, "[{\"fundCode\":\"022485\",\"fundName\":\"Fake Fund\",\"fundType\":null,\"pinyinAbbreviation\":null,\"pinyinFullName\":null}]", catalogueCalls));
        server.createContext("/funds/022485/navs", exchange -> respond(exchange, navResponse(exchange.getRequestURI().getQuery()), navCalls));
        server.start();
        return server;
        } catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    @AfterAll
    static void stopFakePython() { python.stop(0); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ariadne.python-base-url", () -> "http://127.0.0.1:" + python.getAddress().getPort());
    }

    @Test
    void fetchesCatalogueAndNavThenUsesTheRangeCacheAndFetchesOnlyTheExtension() {
        var base = "http://127.0.0.1:" + port + "/funds/022485/navs?startDate=2025-01-01&endDate=";
        assertEquals(200, get(base + "2025-01-03"));
        assertEquals(1, catalogueCalls.get());
        assertEquals(1, navCalls.get());

        assertEquals(200, get(base + "2025-01-03"));
        assertEquals(1, catalogueCalls.get());
        assertEquals(1, navCalls.get());

        assertEquals(200, get(base + "2025-01-05"));
        assertEquals(2, navCalls.get());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String response, AtomicInteger calls) throws java.io.IOException {
        calls.incrementAndGet();
        var body = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String navResponse(String query) {
        var values = new String[]{"1.0000000000", "1.1000000000", "1.2000000000", "1.3000000000", "1.4000000000"};
        var start = Integer.parseInt(query.substring("startDate=2025-01-".length(), "startDate=2025-01-".length() + 2));
        var end = Integer.parseInt(query.substring(query.length() - 2));
        var navs = new StringBuilder("[");
        for (var day = start; day <= end; day++) {
            if (day > start) navs.append(',');
            navs.append("{\"fundCode\":\"022485\",\"navDate\":\"2025-01-").append(String.format("%02d", day)).append("\",\"unitNav\":\"").append(values[day - 1]).append("\"}");
        }
        return navs.append(']').toString();
    }

    private static int get(String uri) {
        try {
            return java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(uri)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
