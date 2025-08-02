package io.korus.dev;

import io.korus.context.ApplicationContext;
import io.korus.web.WebServer;
import io.korus.web.RequestHandler;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DevModeManager {
    private ApplicationContext context;
    private WebServer webServer;
    private DirectoryWatcher watcher;
    private final String basePackage;
    private final Path sourceDirectory;
    private final Path outputDirectory;
    private final Set<Path> changedFiles = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean reloadInProgress = false;

    public DevModeManager(ApplicationContext context, WebServer webServer, String basePackage) {
        this.context = context;
        this.webServer = webServer;
        this.basePackage = basePackage;
        this.sourceDirectory = Paths.get("src/main/java");
        this.outputDirectory = Paths.get("target/classes");
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
                    System.out.println("🔥 Hot reload enabled - watching: " + sourceDirectory.toAbsolutePath());
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

        System.out.println("📝 File changed: " + changedFile.getFileName());
        System.out.println("📂 Event type: " + event.eventType());

        synchronized (changedFiles) {
            changedFiles.add(changedFile);
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                if (!reloadInProgress) {
                    triggerReload();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void triggerReload() {
        if (reloadInProgress) {
            return;
        }

        reloadInProgress = true;
        System.out.println("\n🔄 Starting hot reload...");

        try {
            boolean compiled = compileAllSources();
            if (!compiled) {
                System.err.println("❌ Compilation failed - keeping current version");
                return;
            }

            System.out.println("🛑 Stopping web server...");
            webServer.stop();

            System.out.println("🔄 Reloading application context...");
            context = new ApplicationContext();
            context.scan(basePackage);
            context.start();

            System.out.println("🚀 Starting web server...");
            int port = getServerPort();
            webServer = new WebServer(port);
            RequestHandler handler = new RequestHandler(context);
            webServer.setHandler(handler);
            webServer.start();

            System.out.println("✅ Hot reload completed successfully!");
            System.out.println("🌐 Server restarted at: http://localhost:" + port);

        } catch (Exception e) {
            System.err.println("❌ Hot reload failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            synchronized (changedFiles) {
                changedFiles.clear();
            }
            reloadInProgress = false;
        }
    }

    private boolean compileAllSources() {
        try {
            System.out.println("🔨 Compiling all source files...");

            List<Path> allSourceFiles = Files.walk(sourceDirectory)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("target"))
                    .collect(Collectors.toList());

            if (allSourceFiles.isEmpty()) {
                System.out.println("⚠️ No source files found to compile");
                return true;
            }

            System.out.println("📁 Found " + allSourceFiles.size() + " source files to compile");

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                System.err.println("❌ Java compiler not available. Make sure you're running with JDK");
                return false;
            }

            List<String> options = Arrays.asList(
                    "-d", outputDirectory.toString(),
                    "-cp", getClasspath(),
                    "-source", "21",
                    "-target", "21",
                    "-encoding", "UTF-8"
            );

            List<String> sourceFiles = allSourceFiles.stream()
                    .map(Path::toString)
                    .collect(Collectors.toList());

            List<String> compilerArgs = new ArrayList<>(options);
            compilerArgs.addAll(sourceFiles);

            int result = compiler.run(null, null, null,
                    compilerArgs.toArray(new String[0]));

            if (result == 0) {
                System.out.println("✅ Compilation successful");
                return true;
            } else {
                System.err.println("❌ Compilation failed with exit code: " + result);
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Compilation error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String getClasspath() {
        String classpath = System.getProperty("java.class.path");
        String targetClasses = outputDirectory.toString();
        if (!classpath.contains(targetClasses)) {
            classpath = targetClasses + File.pathSeparator + classpath;
        }
        return classpath;
    }

    private int getServerPort() {
        return 8080;
    }

    public void stop() {
        System.out.println("🛑 Stopping hot reload...");

        if (watcher != null) {
            try {
                watcher.close();
            } catch (Exception e) {
                System.err.println("Error stopping watcher: " + e.getMessage());
            }
        }

        if (webServer != null) {
            try {
                webServer.stop();
            } catch (Exception e) {
                System.err.println("Error stopping web server: " + e.getMessage());
            }
        }

        System.out.println("✅ Hot reload stopped");
    }
}
