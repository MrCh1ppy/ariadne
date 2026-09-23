package com.ariadne.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HttpFundSourceTest {
    @Test
    void callsThePythonHistoryProtocolAndReadsItsJson() throws Exception {
        var requestUri = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/funds/022485/navs", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            var body = "[{\"fundCode\":\"022485\",\"navDate\":\"2025-01-02\",\"unitNav\":\"1.2345000000\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var source = new HttpFundSource("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), new ObjectMapper(), HttpClient.newHttpClient());

            assertEquals(List.of(new RemoteFundNav("022485", "2025-01-02", "1.2345000000")),
                    source.getHistory("022485", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)));
            assertEquals("/funds/022485/navs?startDate=2025-01-01&endDate=2025-01-31", requestUri.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callsThePythonTradeCalendarProtocol() throws Exception {
        var requestUri = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/trade-dates", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            var body = "{\"coverageStart\":\"2024-01-01\",\"coverageEnd\":\"2024-01-31\",\"tradeDates\":[\"2024-01-02\",\"2024-01-03\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var source = new HttpFundSource("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), new ObjectMapper(), HttpClient.newHttpClient());

            assertEquals(new RemoteTradeCalendar(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                            List.of(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3))),
                    source.getTradeDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
            assertEquals("/trade-dates?startDate=2024-01-01&endDate=2024-01-31", requestUri.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsAnInvalidTradeCalendarResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/trade-dates", exchange -> {
            var body = "{\"coverageStart\":\"2024-01-01\",\"coverageEnd\":\"2024-01-31\",\"tradeDates\":[\"2024-01-03\",\"2024-01-02\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var source = new HttpFundSource("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), new ObjectMapper(), HttpClient.newHttpClient());
            org.junit.jupiter.api.Assertions.assertThrows(UpstreamException.class,
                    () -> source.getTradeDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
        } finally {
            server.stop(0);
        }
    }
}
