package com.ariadne.extraction;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpFundSource implements FundSource {
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    @Autowired
    public HttpFundSource(
            @Value("${ariadne.python-base-url}") String baseUrl,
            @Value("${ariadne.http-timeout}") Duration timeout,
            ObjectMapper objectMapper
    ) {
        this(baseUrl, timeout, objectMapper, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    HttpFundSource(String baseUrl, Duration timeout, ObjectMapper objectMapper, HttpClient client) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public List<RemoteFund> getFunds() {
        return read("/funds", new TypeReference<>() {});
    }

    @Override
    public List<RemoteFundNav> getHistory(String fundCode, LocalDate startDate, LocalDate endDate) {
        return read("/funds/" + fundCode + "/navs?startDate=" + startDate + "&endDate=" + endDate, new TypeReference<>() {});
    }

    private <T> List<T> read(String path, TypeReference<List<T>> type) {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new UpstreamException("Python adapter returned HTTP " + response.statusCode());
            }
            var result = objectMapper.readValue(response.body(), type);
            if (result == null) throw new UpstreamException("Python adapter returned an invalid response");
            return result;
        } catch (UpstreamException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UpstreamException("Python adapter request failed", exception);
        }
    }
}
