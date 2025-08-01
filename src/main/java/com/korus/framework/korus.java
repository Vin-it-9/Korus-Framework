package com.korus.framework;

import com.korus.framework.config.ConfigurationManager;
import com.korus.framework.console.Logger;
import com.korus.framework.context.ApplicationContext;
import com.korus.framework.web.RequestHandler;
import com.korus.framework.web.WebServer;
import com.korus.framework.dev.DevModeManager;

import java.lang.management.ManagementFactory;

public class korus {

    public static ApplicationContext run(Class<?> mainClass, String[] args) {

        long startTime = System.currentTimeMillis();
        long jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

        boolean devMode = isDevMode(args);
        Logger logger = new Logger("korus-framework");

        try {
            ApplicationContext context = new ApplicationContext();
            context.scan(mainClass.getPackageName());
            context.start();
            ConfigurationManager config = ConfigurationManager.getInstance();
            int port = config.getIntProperty("server.port", 8080);
            logger.logWebServerInitializing(port);
            logger.logWebServerStarting();
            WebServer server = new WebServer(port);
            RequestHandler handler = new RequestHandler(context);
            server.setHandler(handler);
            server.start();
            long totalStartupTime = System.currentTimeMillis() - startTime;
            double actualStartupSeconds = totalStartupTime / 1000.0;
            double actualJvmRuntime = (System.currentTimeMillis() - jvmStartTime) / 1000.0;
            logger.logWebServerStarted(port, actualStartupSeconds, actualJvmRuntime);
            if (devMode) {
                logger.info("c.k.f.dev.DevModeManager", "Starting development mode with hot reload");
                DevModeManager devManager = new DevModeManager(context, server, mainClass.getPackageName());
                devManager.startWatching();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    logger.info("c.k.f.KorusApplication", "Shutting down application...");
                    devManager.stop();
                    server.stop();
                    logger.info("c.k.f.KorusApplication", "Application shutdown completed");
                }));
                logger.info("c.k.f.dev.DevModeManager", "Hot reload is active - modify your Java files and see changes instantly!");
            }

            return context;

        } catch (Exception e) {
            Logger errorLogger = new Logger("korus-framework");
            errorLogger.error("c.k.f.KorusApplication", "Application run failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to start ApplicationContext: " + e.getMessage(), e);
        }
    }

    private static boolean isDevMode(String[] args) {
        if (args == null) return true;

        for (String arg : args) {
            if ("--prod".equals(arg) || "--production".equals(arg)) {
                return false;
            }
            if ("--dev".equals(arg) || "--development".equals(arg)) {
                return true;
            }
        }

        String devModeProperty = System.getProperty("korus.dev.mode");
        if (devModeProperty != null) {
            return Boolean.parseBoolean(devModeProperty);
        }

        return true;
    }

    public static ApplicationContext run(Class<?> mainClass, String[] args, int port) {
        System.setProperty("server.port", String.valueOf(port));
        return run(mainClass, args);
    }

    public static ApplicationContext runProduction(Class<?> mainClass, String[] args) {
        String[] productionArgs = new String[args.length + 1];
        System.arraycopy(args, 0, productionArgs, 0, args.length);
        productionArgs[args.length] = "--production";
        return run(mainClass, productionArgs);
    }

    public static String getVersion() {
        return "1.0.0";
    }

    public static void printFrameworkInfo() {
        System.out.println("Korus Framework v" + getVersion());
        System.out.println("A lightweight, Spring Boot-inspired Java framework");
        System.out.println("Features: IoC Container, JPA Integration, Web Server, Hot Reload, Transactions");
    }

    private korus() {
        throw new AssertionError("This class should not be instantiated");
    }
}
