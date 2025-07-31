package com.korus.framework;

import com.korus.framework.config.ConfigurationManager;
import com.korus.framework.context.ApplicationContext;
import com.korus.framework.web.RequestHandler;
import com.korus.framework.web.WebServer;
import com.korus.framework.dev.DevModeManager;

public class korus {

    public static ApplicationContext run(Class<?> mainClass, String[] args) {
        boolean devMode = isDevMode(args);

        ApplicationContext context = new ApplicationContext();
        context.scan(mainClass.getPackageName());
        try {
            context.start();
        } catch(Exception e) {
            throw new RuntimeException("Failed to start ApplicationContext: " + e.getMessage(), e);
        }

        ConfigurationManager config = ConfigurationManager.getInstance();
        int port = config.getIntProperty("server.port", 8080);

        WebServer server = new WebServer(port);
        RequestHandler handler = new RequestHandler(context);
        server.setHandler(handler);
        server.start();

        if (devMode) {
            DevModeManager devManager = new DevModeManager(context, server, mainClass.getPackageName());
            devManager.startWatching();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                devManager.stop();
                server.stop();
            }));
        }

        return context;
    }

    private static boolean isDevMode(String[] args) {
        if (args == null) return true;
        for (String arg : args) {
            if ("--prod".equals(arg) || "--production".equals(arg)) return false;
            if ("--dev".equals(arg) || "--development".equals(arg)) return true;
        }
        return true;
    }
}
