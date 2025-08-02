package io.korus.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.korus.context.ApplicationContext;
import io.korus.template.ThymeleafConfig;
import io.korus.web.annotaion.PathVariable;
import io.korus.web.annotaion.RequestBody;
import io.korus.web.annotaion.RequestParam;
import io.undertow.server.*;
import io.undertow.util.*;
import org.thymeleaf.context.Context;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

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

        if (isStaticResource(path)) {
            StaticResourceHandler staticHandler = new StaticResourceHandler("static");
            staticHandler.handleRequest(exchange);
            return;
        }

        String matchedRoute = findMatchingRoute(path);
        Map<String, ApplicationContext.ControllerMethod> methodMap = context.getRoutes().get(matchedRoute != null ? matchedRoute : path);

        if (methodMap != null) {
            ApplicationContext.ControllerMethod controllerMethod = methodMap.get(method);

            if (controllerMethod != null) {
                if (needsRequestBody(method)) {
                    exchange.dispatch(() -> {
                        try {
                            handleBlockingRequest(exchange, controllerMethod, method, matchedRoute != null ? matchedRoute : path);
                        } catch (Exception e) {
                            e.printStackTrace();
                            try {
                                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    });
                } else {
                    handleControllerMethod(exchange, controllerMethod, method, matchedRoute != null ? matchedRoute : path);
                }
            } else {
                sendError(exchange, 405, "Method " + method + " not allowed for " + path);
            }
        } else {

        }
    }

    private String findMatchingRoute(String requestPath) {
        for (String route : context.getRoutes().keySet()) {
            if (route.contains("{")) {
                String regex = route.replaceAll("\\{[^}]+\\}", "[^/]+");
                if (requestPath.matches(regex)) {
                    return route;
                }
            }
        }
        return null;
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
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
    }

    private void handleBlockingRequest(HttpServerExchange exchange, ApplicationContext.ControllerMethod controllerMethod, String method, String routeTemplate) throws Exception {
        handleControllerMethod(exchange, controllerMethod, method, routeTemplate);
    }

    private void handleControllerMethod(HttpServerExchange exchange, ApplicationContext.ControllerMethod controllerMethod, String httpMethod, String routeTemplate) throws Exception {
        try {
            Object result;
            Method method = controllerMethod.getMethod();
            Parameter[] parameters = method.getParameters();
            Model model = new Model();

            if (parameters.length == 0) {
                result = method.invoke(controllerMethod.getController());
            } else {
                Object[] args = resolveMethodParametersEnhanced(exchange, method, routeTemplate, httpMethod, model);
                result = method.invoke(controllerMethod.getController(), args);
            }
            handleResponse(exchange, result, model, httpMethod);

        } catch (Exception e) {
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
    }

    private void handleRedirect(HttpServerExchange exchange, String redirectUrl) throws Exception {
        String url = redirectUrl.substring("redirect:".length());
        exchange.setStatusCode(302);
        exchange.getResponseHeaders().put(HttpString.tryFromString("Location"), url);
        exchange.getResponseSender().send("");
    }

    private void sendJsonResponse(HttpServerExchange exchange, Object result) throws Exception {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
        String jsonResponse = objectMapper.writeValueAsString(result);
        exchange.getResponseSender().send(jsonResponse);
    }

    private Object[] resolveMethodParametersEnhanced(HttpServerExchange exchange, Method method, String routeTemplate, String httpMethod, Model model) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        String actualPath = exchange.getRequestPath();
        Map<String, String> queryParams = extractQueryParameters(exchange);

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];

            if (param.getType() == Model.class) {
                args[i] = model;
            } else if (param.isAnnotationPresent(PathVariable.class)) {
                String varName = param.getAnnotation(PathVariable.class).value();
                if (varName.isEmpty()) varName = param.getName();

                String value = extractPathVariableAdvanced(actualPath, routeTemplate, varName);
                args[i] = convertParameter(value, param.getType());
            } else if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam annotation = param.getAnnotation(RequestParam.class);
                String paramName = annotation.value();
                if (paramName.isEmpty()) paramName = param.getName();

                String value = queryParams.get(paramName);
                if (value == null && annotation.required()) {
                    throw new RuntimeException("Required request parameter '" + paramName + "' is missing");
                }
                args[i] = convertParameter(value, param.getType());
            } else if (param.isAnnotationPresent(RequestBody.class)) {
                args[i] = resolveRequestBodyParameter(exchange, param, httpMethod);
            } else {
                args[i] = getDefaultValue(param.getType());
            }
        }

        return args;
    }

    private Object resolveRequestBodyParameter(HttpServerExchange exchange, Parameter parameter, String httpMethod) throws Exception {

        if (!"POST".equalsIgnoreCase(httpMethod) && !"PUT".equalsIgnoreCase(httpMethod) && !"PATCH".equalsIgnoreCase(httpMethod)) {
            throw new UnsupportedOperationException("@RequestBody can only be used with POST, PUT, or PATCH requests");
        }

        exchange.startBlocking();

        try (InputStream inputStream = exchange.getInputStream()) {
            StringBuilder bodyBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    bodyBuilder.append(line);
                }
            }

            String body = bodyBuilder.toString().trim();
            if (body.isEmpty()) {
                return null;
            }

            Class<?> paramType = parameter.getType();

            try {
                return objectMapper.readValue(body, paramType);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse JSON request body to " + paramType.getSimpleName() + ": " + e.getMessage(), e);
            }
        }
    }

    private String extractPathVariable(String actualPath, String variableName) {
        String[] pathSegments = actualPath.split("/");

        if ("id".equals(variableName)) {
            for (int i = pathSegments.length - 1; i >= 0; i--) {
                if (pathSegments[i].matches("\\d+")) {
                    return pathSegments[i];
                }
            }
        }

        if (pathSegments.length > 0) {
            return pathSegments[pathSegments.length - 1];
        }

        return null;
    }

    private String extractPathVariableAdvanced(String actualPath, String routeTemplate, String variableName) {
        if (routeTemplate == null || actualPath == null) {
            return extractPathVariable(actualPath, variableName);
        }

        String regexPattern = routeTemplate.replaceAll("\\{[^}]+\\}", "([^/]+)");
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(actualPath);

        if (matcher.matches()) {
            String[] templateParts = routeTemplate.split("/");
            int groupIndex = 0;

            for (String part : templateParts) {
                if (part.startsWith("{") && part.endsWith("}")) {
                    groupIndex++;
                    String varName = part.substring(1, part.length() - 1);
                    if (varName.equals(variableName)) {
                        return matcher.group(groupIndex);
                    }
                }
            }
        }

        return null;
    }

    private Object convertParameter(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return getDefaultValue(targetType);
        }

        value = value.trim();

        try {
            if (targetType.equals(String.class)) {
                return value;
            } else if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
                return Integer.parseInt(value);
            } else if (targetType.equals(Long.class) || targetType.equals(long.class)) {
                return Long.parseLong(value);
            } else if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
                return Boolean.parseBoolean(value);
            } else if (targetType.equals(Double.class) || targetType.equals(double.class)) {
                return Double.parseDouble(value);
            } else if (targetType.equals(Float.class) || targetType.equals(float.class)) {
                return Float.parseFloat(value);
            } else if (targetType.equals(Short.class) || targetType.equals(short.class)) {
                return Short.parseShort(value);
            } else if (targetType.equals(Byte.class) || targetType.equals(byte.class)) {
                return Byte.parseByte(value);
            } else if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, value.toUpperCase());
            } else if (List.class.isAssignableFrom(targetType)) {
                String[] values = value.split(",");
                List<String> list = new ArrayList<>();
                for (String v : values) {
                    list.add(v.trim());
                }
                return list;
            }

            return objectMapper.readValue("\"" + value + "\"", targetType);

        } catch (Exception e) {
            throw new RuntimeException("Failed to convert parameter value '" + value + "' to type " + targetType.getSimpleName(), e);
        }
    }

    private Object getDefaultValue(Class<?> type) {
        if (type.equals(int.class)) return 0;
        if (type.equals(long.class)) return 0L;
        if (type.equals(double.class)) return 0.0;
        if (type.equals(float.class)) return 0.0f;
        if (type.equals(boolean.class)) return false;
        if (type.equals(short.class)) return (short) 0;
        if (type.equals(byte.class)) return (byte) 0;
        if (type.equals(char.class)) return '\0';
        return null;
    }

    private Object parseRequestBody(HttpServerExchange exchange, Class<?> paramType) throws IOException {
        InputStream inputStream = exchange.getInputStream();

        try {
            return objectMapper.readValue(inputStream, paramType);
        } catch (Exception e) {
            throw new IOException("Failed to parse request body to " + paramType.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private Map<String, String> extractQueryParameters(HttpServerExchange exchange) {
        Map<String, String> queryParams = new HashMap<>();

        Map<String, Deque<String>> parameters = exchange.getQueryParameters();
        for (Map.Entry<String, Deque<String>> entry : parameters.entrySet()) {
            Deque<String> values = entry.getValue();
            if (!values.isEmpty()) {
                queryParams.put(entry.getKey(), values.getFirst());
            }
        }

        return queryParams;
    }

    private String readRequestBody(HttpServerExchange exchange) throws Exception {
        exchange.startBlocking();

        StringBuilder body = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = exchange.getInputStream().read(buffer)) != -1) {
            body.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private void sendNotFound(HttpServerExchange exchange, String message) throws Exception {
        exchange.setStatusCode(404);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
        String errorResponse = objectMapper.writeValueAsString(
                new ErrorResponse("Not Found", message)
        );
        exchange.getResponseSender().send(errorResponse);
        System.out.println(message);
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
        private final Map<String, Object> attributes = new HashMap<>();

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
