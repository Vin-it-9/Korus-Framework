package com.korus.framework.web;

import java.util.HashMap;
import java.util.Map;

public class MimeTypeResolver {
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=UTF-8");
        MIME_TYPES.put("htm", "text/html; charset=UTF-8");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("ttf", "font/ttf");
        MIME_TYPES.put("eot", "application/vnd.ms-fontobject");
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("txt", "text/plain");
        MIME_TYPES.put("xml", "application/xml");
    }

    public static String getMimeType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "application/octet-stream";
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
