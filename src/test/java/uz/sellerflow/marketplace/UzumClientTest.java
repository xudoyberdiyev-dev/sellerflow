package uz.sellerflow.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.sellerflow.marketplace.UzumClient.Failure;
import uz.sellerflow.marketplace.UzumClient.UzumException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UzumClientTest {

    HttpServer server;
    UzumClient client;
    final AtomicReference<String> auth = new AtomicReference<>();
    final AtomicInteger calls = new AtomicInteger();
    volatile int status = 200;
    volatile String contentType = "application/json";
    volatile String body = "[{\"id\":12345,\"name\":\"Do'kon\"}]";
    volatile String remaining = "500";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/seller-openapi", ex -> {
            calls.incrementAndGet();
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.getResponseHeaders().add("x-ratelimit-remaining-per-day", remaining);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        client = new UzumClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/seller-openapi", 0, new ObjectMapper());
        client.setSleeper(ms -> { });
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void shops_rawTokenWithoutBearer() {
        var shops = client.shops("tok_abcdefghij");
        assertThat(shops).singleElement().satisfies(s -> assertThat(s.id()).isEqualTo(12345L));
        assertThat(auth.get()).isEqualTo("tok_abcdefghij");
    }

    @Test
    void products_pageParsing() {
        body = "{\"productList\":[{\"productId\":1},{\"productId\":2}],\"totalProductsAmount\":2}";
        var pg = client.products("tok_abcdefghij", 5, 0);
        assertThat(pg.products()).hasSize(2);
        assertThat(pg.total()).isEqualTo(2);
    }

    @Test
    void errorClassification() {
        status = 401;
        assertThatThrownBy(() -> client.shops("tok_abcdefghij")).isInstanceOf(UzumException.class)
                .extracting(e -> ((UzumException) e).failure()).isEqualTo(Failure.INVALID_TOKEN);
        status = 403;
        contentType = "text/html";
        body = "<html>blocked</html>";
        assertThatThrownBy(() -> client.shops("tok_abcdefghij")).extracting(e -> ((UzumException) e).failure()).isEqualTo(Failure.WAF_BLOCKED);
        status = 429;
        contentType = "application/json";
        remaining = "0";
        calls.set(0);
        assertThatThrownBy(() -> client.shops("tok_abcdefghij")).extracting(e -> ((UzumException) e).failure()).isEqualTo(Failure.QUOTA_EXHAUSTED);
        assertThat(calls.get()).as("kunlik 429 da qayta urinilmaydi").isEqualTo(1);
        remaining = "100";
        calls.set(0);
        assertThatThrownBy(() -> client.shops("tok_abcdefghij")).extracting(e -> ((UzumException) e).failure()).isEqualTo(Failure.RATE_LIMITED);
        assertThat(calls.get()).isEqualTo(UzumClient.MAX_ATTEMPTS);
        status = 503;
        calls.set(0);
        assertThatThrownBy(() -> client.shops("tok_abcdefghij")).extracting(e -> ((UzumException) e).failure()).isEqualTo(Failure.SERVER);
        assertThat(calls.get()).isEqualTo(UzumClient.MAX_ATTEMPTS);
    }
}
