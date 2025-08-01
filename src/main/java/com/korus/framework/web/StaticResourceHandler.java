package com.korus.framework.web;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class StaticResourceHandler implements HttpHandler {
    private final String resourcePrefix;

    public StaticResourceHandler(String resourcePrefix) {
        this.resourcePrefix = resourcePrefix;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        String resourcePath = resourcePrefix + path;
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

        if (inputStream != null) {
            try {
                String contentType = MimeTypeResolver.getMimeType(path);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
                addCachingHeaders(exchange, path);
                byte[] content = inputStream.readAllBytes();
                exchange.getResponseSender().send(ByteBuffer.wrap(content));
            } finally {
                inputStream.close();
            }
        } else {
            exchange.setStatusCode(404);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Static resource not found: " + path);
        }
    }

    private void addCachingHeaders(HttpServerExchange exchange, String path) {
        long cacheSeconds;
        if (path.endsWith(".css") || path.endsWith(".js")) {
            cacheSeconds = 3600;
        } else if (path.matches(".*\\.(png|jpg|jpeg|gif|svg|ico)$")) {
            cacheSeconds = 86400;
        } else {
            cacheSeconds = 1800;
        }
        exchange.getResponseHeaders().put(HttpString.tryFromString("Cache-Control"), "public, max-age=" + cacheSeconds);
        String lastModified = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        exchange.getResponseHeaders().put(HttpString.tryFromString("Last-Modified"), lastModified);
    }
}
