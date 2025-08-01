package com.korus.framework.dev;

import com.korus.framework.context.ApplicationContext;
import com.korus.framework.web.WebServer;
import com.korus.framework.web.RequestHandler;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DevModeManager {
    private Object reloadableContext;
    private WebServer webServer;
    private DirectoryWatcher watcher;
    private final String basePackage;
    private final Path sourceDirectory;
    private final Path outputDirectory;
    private final Set<Path> changedFiles = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean reloadInProgress = false;
    private URLClassLoader reloadableClassLoader;

    public DevModeManager(ApplicationContext context, WebServer webServer, String basePackage) {
        this.reloadableContext = context;
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
                Thread.sleep(1000); // Increased debounce time for safer compilation
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
            // Step 1: Clean and compile ALL source files (not just changed ones)
            boolean compiled = compileAllSources();
            if (!compiled) {
                System.err.println("❌ Compilation failed - keeping current version");
                System.err.println("💡 Please fix compilation errors and save again");
                return;
            }

            // Step 2: Stop existing server
            System.out.println("🛑 Stopping web server...");
            webServer.stop();

            // Step 3: Close old class loader
            closeOldClassLoader();

            // Step 4: Create new class loader
            System.out.println("🔄 Creating new ClassLoader...");
            createApplicationClassLoader();

            // Step 5: Create and initialize new context
            System.out.println("🔄 Reloading application context...");
            reloadableContext = createAndInitializeContext();

            // Step 6: Start new web server
            System.out.println("🚀 Starting web server...");
            int port = getServerPort();
            webServer = new WebServer(port);
            RequestHandler handler = createRequestHandler();
            webServer.setHandler(handler);
            webServer.start();

            System.out.println("✅ Hot reload completed successfully!");
            System.out.println("🌐 Server restarted at: http://localhost:" + port);

        } catch (Exception e) {
            System.err.println("❌ Hot reload failed: " + e.getMessage());
            e.printStackTrace();

            // Attempt to restart with old context
            try {
                System.out.println("🔄 Attempting to restart with previous version...");
                webServer = new WebServer(getServerPort());
                RequestHandler handler = createRequestHandler();
                webServer.setHandler(handler);
                webServer.start();
                System.out.println("✅ Restarted with previous version");
            } catch (Exception restartException) {
                System.err.println("❌ Failed to restart with previous version: " + restartException.getMessage());
            }

        } finally {
            synchronized (changedFiles) {
                changedFiles.clear();
            }
            reloadInProgress = false;
        }
    }

    private boolean compileAllSources() {
        try {
            System.out.println("🧹 Cleaning target/classes directory...");

            // Clean application classes (keep framework and dependency classes)
            cleanApplicationClasses();

            System.out.println("🔨 Compiling all source files...");

            // Get all Java source files
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

            // Combine options and source files
            List<String> compilerArgs = new ArrayList<>(options);
            compilerArgs.addAll(sourceFiles);

            int result = compiler.run(null, System.out, System.err,
                    compilerArgs.toArray(new String[0]));

            if (result == 0) {
                System.out.println("✅ Compilation successful");
                return true;
            } else {
                System.err.println("❌ Compilation failed with exit code: " + result);
                System.err.println("💡 Check the error messages above and fix the compilation issues");
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Compilation error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void cleanApplicationClasses() {
        try {
            // Only clean classes from our application package
            String packagePath = basePackage.replace('.', File.separatorChar);
            Path packageDir = outputDirectory.resolve(packagePath);

            if (Files.exists(packageDir)) {
                Files.walk(packageDir)
                        .filter(path -> path.toString().endsWith(".class"))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (Exception e) {
                                // Ignore deletion errors
                            }
                        });
                System.out.println("🗑️ Cleaned application classes from: " + packageDir);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to clean application classes: " + e.getMessage());
        }
    }

    private void closeOldClassLoader() {
        if (reloadableClassLoader != null) {
            try {
                reloadableClassLoader.close();
                System.out.println("🗑️ Closed old ClassLoader");
            } catch (Exception e) {
                System.err.println("⚠️ Failed to close old ClassLoader: " + e.getMessage());
            }
        }
    }

    private void createApplicationClassLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(outputDirectory.toUri().toURL());

        reloadableClassLoader = new ApplicationReloadClassLoader(
                urls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader(),
                basePackage
        );

        System.out.println("🔄 Created application ClassLoader for package: " + basePackage);
    }

    private Object createAndInitializeContext() throws Exception {
        Class<?> contextClass = reloadableClassLoader.loadClass("com.korus.framework.context.ApplicationContext");
        Object contextInstance = contextClass.getDeclaredConstructor().newInstance();

        Method scanMethod = contextClass.getMethod("scan", String.class);
        scanMethod.invoke(contextInstance, basePackage);

        Method startMethod = contextClass.getMethod("start");
        startMethod.invoke(contextInstance);

        System.out.println("✅ ApplicationContext created and initialized");
        return contextInstance;
    }

    private RequestHandler createRequestHandler() throws Exception {
        Class<?> handlerClass = RequestHandler.class;
        return (RequestHandler) handlerClass.getDeclaredConstructor(Object.class)
                .newInstance(reloadableContext);
    }

    private static class ApplicationReloadClassLoader extends URLClassLoader {
        private final String applicationPackage;

        public ApplicationReloadClassLoader(URL[] urls, ClassLoader parent, String applicationPackage) {
            super(urls, parent);
            this.applicationPackage = applicationPackage;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // NEVER reload framework classes - always delegate to parent
            if (name.startsWith("com.korus.framework.annotations") ||
                    name.startsWith("com.korus.framework.web") ||
                    name.startsWith("com.korus.framework.context") ||
                    name.startsWith("com.korus.framework.data") ||
                    name.startsWith("com.korus.framework.transaction") ||
                    name.startsWith("com.korus.framework.config") ||
                    name.startsWith("com.korus.framework.console")) {
                return super.loadClass(name);
            }

            // Only reload application classes (controllers, services, entities)
            if (name.startsWith(applicationPackage)) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException e) {
                    // Fall back to parent if not found
                }
            }

            return super.loadClass(name);
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

        closeOldClassLoader();
        System.out.println("✅ Hot reload stopped");
    }
}
