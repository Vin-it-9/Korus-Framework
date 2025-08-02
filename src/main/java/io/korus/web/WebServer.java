package io.korus.web;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;

public class WebServer {

    private Undertow server;
    private final int port;
    private HttpHandler handler;

    public WebServer(int port) {
        this.port = port;
    }

    public void setHandler(HttpHandler handler) {
        this.handler = handler;
    }

    public void start() {
        server = Undertow.builder()
                .addHttpListener(port, "localhost")
                .setHandler(handler)
                .build();

        server.start();
        System.out.println("Web server started on http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
