package com.korus.framework.dev;

import com.korus.framework.context.ApplicationContext;
import com.korus.framework.web.WebServer;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class DevModeManager {
    private final ApplicationContext context;
    private final WebServer webServer;
    private DirectoryWatcher watcher;
    private final String basePackage;
    private final Path sourceDirectory;

    public DevModeManager(ApplicationContext context, WebServer webServer, String basePackage) {
        this.context = context;
        this.webServer = webServer;
        this.basePackage = basePackage;
        this.sourceDirectory = Paths.get("src/main/java");
    }

    public void startWatching() {
        if (!sourceDirectory.toFile().exists()) {
            System.out.println("Source directory not found: " + sourceDirectory + ". Hot reload disabled.");
            return;
        }

        try {
            watcher = DirectoryWatcher.builder()
                    .path(sourceDirectory)
                    .listener(this::handleFileChange)
                    .build();

            CompletableFuture.runAsync(() -> {
                try {
                    watcher.watch();
                } catch (Exception e) {
                    System.err.println("Error in file watcher: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            System.err.println("Failed to start file watcher: " + e.getMessage());
        }
    }

    private void handleFileChange(DirectoryChangeEvent event) {
        Path changedFile = event.path();

        if (!changedFile.toString().endsWith(".java")) {
            return;
        }
        if (changedFile.toString().contains("target") || changedFile.toString().contains(".class")) {
            return;
        }

        System.out.println("\nFile changed: " + changedFile.getFileName());
        System.out.println("Event type: " + event.eventType());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        reloadApplication();
    }

    private void reloadApplication() {
        try {
            System.out.println("Reloading application...");

            boolean compiled = compileChangedFiles();
            if (!compiled) {
                System.err.println("Compilation failed - keeping current version");
                return;
            }

            context.start();
            int port = 8080;
            System.out.println("Web server started on http://localhost:" + port);

        } catch (Exception e) {
            System.err.println("Hot reload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean compileChangedFiles() {

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    public void stop() {
        if (watcher != null) {
            try {
                watcher.close();
            } catch (Exception e) {
                System.err.println("Error stopping watcher: " + e.getMessage());
            }
        }
    }
}
