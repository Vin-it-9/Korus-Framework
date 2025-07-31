package com.korus.framework.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korus.framework.context.ApplicationContext;
import com.korus.framework.annotations.RequestBody;
import com.korus.framework.template.ThymeleafConfig;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.thymeleaf.context.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RequestHandler implements HttpHandler {
    private final ApplicationContext context;
    private final ObjectMapper objectMapper;

    public RequestHandler(ApplicationContext context) {
        this.context = context;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        String method = exchange.getRequestMethod().toString();
        System.out.println("🌐 Received request: " + method + " " + path);
        if (isStaticResource(path)) {
            StaticResourceHandler staticHandler = new StaticResourceHandler("static");
            staticHandler.handleRequest(exchange);
            return;
        }

        Map<String, ApplicationContext.ControllerMethod> methodMap = context.getRoutes().get(path);

        if (methodMap != null) {
            ApplicationContext.ControllerMethod controllerMethod = methodMap.get(method);

            if (controllerMethod != null) {
                if (needsRequestBody(method)) {
                    exchange.dispatch(this::handleBlockingRequest);
                } else {
                    handleControllerMethod(exchange, controllerMethod, method);
                }
            } else {
                sendError(exchange, 405, "Method " + method + " not allowed for " + path);
            }
        } else {
            sendNotFound(exchange, "No mapping found for " + path);
        }
    }

    private boolean isStaticResource(String path) {
        return path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.startsWith("/fonts/") ||
                path.startsWith("/favicon.ico") ||
                path.matches(".*\\.(css|html|htm|js|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot|pdf)$");
    }

    private boolean needsRequestBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
    }

    private void handleBlockingRequest(HttpServerExchange exchange) {
        try {
            String path = exchange.getRequestPath();
            String method = exchange.getRequestMethod().toString();

            Map<String, ApplicationContext.ControllerMethod> methodMap = context.getRoutes().get(path);
            ApplicationContext.ControllerMethod controllerMethod = methodMap.get(method);

            handleControllerMethod(exchange, controllerMethod, method);
        } catch (Exception e) {
            try {
                System.err.println("❌ Error in worker thread: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            } catch (Exception ex) {
                System.err.println("Failed to send error response: " + ex.getMessage());
            }
        }
    }

    private void handleControllerMethod(HttpServerExchange exchange,
                                        ApplicationContext.ControllerMethod controllerMethod,
                                        String httpMethod) throws Exception {
        try {
            Object result;
            Method method = controllerMethod.getMethod();
            Parameter[] parameters = method.getParameters();
            Model model = new Model();
            if (parameters.length == 0) {
                result = method.invoke(controllerMethod.getController());
            } else {
                Object[] args = resolveMethodParameters(exchange, parameters, httpMethod, model);
                result = method.invoke(controllerMethod.getController(), args);
            }
            handleResponse(exchange, result, model, httpMethod);

            System.out.println("✅ Successfully handled " + httpMethod + " " + exchange.getRequestPath());

        } catch (Exception e) {
            System.err.println("❌ Error handling request: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleResponse(HttpServerExchange exchange, Object result, Model model, String httpMethod) throws Exception {
        if (result instanceof String) {
            String viewName = (String) result;
            if (viewName.startsWith("redirect:")) {
                handleRedirect(exchange, viewName);
            } else {
                renderTemplate(exchange, viewName, model);
            }
        } else {
            sendJsonResponse(exchange, result);
        }
    }

    private void renderTemplate(HttpServerExchange exchange, String templateName, Model model) throws Exception {
        Context context = new Context();
        context.setVariables(model.getAttributes());

        String html = ThymeleafConfig.getTemplateEngine().process(templateName, context);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8");
        exchange.getResponseSender().send(html);

        System.out.println("🎨 Rendered template: " + templateName);
    }

    private void handleRedirect(HttpServerExchange exchange, String redirectUrl) throws Exception {
        String url = redirectUrl.substring("redirect:".length());
        exchange.setStatusCode(302);
        exchange.getResponseHeaders().put(HttpString.tryFromString("Location"), url);
        exchange.getResponseSender().send("");
        System.out.println("🔄 Redirected to: " + url);
    }

    private void sendJsonResponse(HttpServerExchange exchange, Object result) throws Exception {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
        String jsonResponse = objectMapper.writeValueAsString(result);
        exchange.getResponseSender().send(jsonResponse);
    }

    private Object[] resolveMethodParameters(HttpServerExchange exchange, Parameter[] parameters,
                                             String httpMethod, Model model) throws Exception {
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];

            if (param.getType() == Model.class) {
                args[i] = model;
                System.out.println("📝 Injected Model parameter");
            } else if (param.isAnnotationPresent(RequestBody.class)) {
                args[i] = resolveRequestBodyParameter(exchange, param, httpMethod);
            } else {
                args[i] = null;
            }
        }

        return args;
    }

    private Object resolveRequestBodyParameter(HttpServerExchange exchange, Parameter param, String httpMethod) throws Exception {
        if ("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "DELETE".equals(httpMethod)) {
            String requestBody = readRequestBody(exchange);
            System.out.println("📦 Raw request body: " + requestBody);

            if (requestBody != null && !requestBody.trim().isEmpty()) {
                Object result = objectMapper.readValue(requestBody, param.getType());
                System.out.println("📦 Parsed request body for parameter: " + param.getType().getSimpleName());
                return result;
            } else {
                System.out.println("⚠️ Empty request body, setting parameter to null");
                return null;
            }
        }
        return null;
    }


    private String readRequestBody(HttpServerExchange exchange) throws Exception {

        exchange.startBlocking();

        StringBuilder body = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = exchange.getInputStream().read(buffer)) != -1) {
            body.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }

        String result = body.toString();
        System.out.println("📖 Read request body (" + result.length() + " chars): " + result);
        return result;
    }

    private void sendNotFound(HttpServerExchange exchange, String message) throws Exception {
        exchange.setStatusCode(404);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
        String errorResponse = objectMapper.writeValueAsString(
                new ErrorResponse("Not Found", message)
        );
        exchange.getResponseSender().send(errorResponse);
        System.out.println("❌ " + message);
    }

    private void sendError(HttpServerExchange exchange, int statusCode, String message) throws Exception {
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
        String errorResponse = objectMapper.writeValueAsString(
                new ErrorResponse("Error", message)
        );
        exchange.getResponseSender().send(errorResponse);
    }

    private static class ErrorResponse {
        private final String error;
        private final String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    public static class Model {
        private final Map<String, Object> attributes = new java.util.HashMap<>();

        public Model addAttribute(String name, Object value) {
            attributes.put(name, value);
            return this;
        }

        public Model addAllAttributes(Map<String, Object> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public boolean hasAttribute(String name) {
            return attributes.containsKey(name);
        }

        public Object getAttribute(String name) {
            return attributes.get(name);
        }
    }
}
