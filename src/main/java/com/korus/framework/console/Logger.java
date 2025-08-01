package com.korus.framework.console;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

public class Logger {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'+05:30'");

    private final String applicationName;
    private final String processId;
    private final String mainThread;
    private final AtomicLong startTime;

    public Logger(String applicationName) {
        this.applicationName = applicationName;
        this.processId = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        this.mainThread = "main";
        this.startTime = new AtomicLong(System.currentTimeMillis());
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    private void log(String level, String loggerClass, String message) {
        System.out.printf("%s  %s %s --- [%s] [%15s] %-40s : %s%n",
                getCurrentTimestamp(),
                level,
                processId,
                applicationName,
                mainThread,
                loggerClass,
                message
        );
    }

    public void info(String loggerClass, String message) {
        log("INFO", loggerClass, message);
    }

    public void warn(String loggerClass, String message) {
        log("WARN", loggerClass, message);
    }

    public void error(String loggerClass, String message) {
        log("ERROR", loggerClass, message);
    }

    public void logApplicationStart(String javaVersion) {
        info("c.k.f.KorusApplication",
                String.format("Starting KorusApplication using Java %s", javaVersion));
    }

    public void logProfileActive(String profile) {
        info("c.k.f.KorusApplication",
                String.format("The following 1 profile is active: \"%s\"", profile));
    }

    public void logConfigurationStart() {
        info("c.k.f.config.ConfigurationManager", "Loading configuration properties");
    }

    public void logConfigurationLoaded(String fileName, int actualPropertyCount) {
        info("c.k.f.config.ConfigurationManager",
                String.format("Loaded %s with %d properties", fileName, actualPropertyCount));
    }

    public void logTotalPropertiesLoaded(int totalProperties) {
        info("c.k.f.config.ConfigurationManager",
                String.format("Loaded %d configuration properties total", totalProperties));
    }

    public void logEntitiesFound(int actualEntityCount, String[] entityNames) {
        info("o.h.jpa.internal.util.LogHelper", "HHH000204: Processing PersistenceUnitInfo [name: default]");
        if (actualEntityCount > 0) {
            info("c.k.f.entity.EntityScanner",
                    String.format("Found %d JPA entities: [%s]", actualEntityCount, String.join(", ", entityNames)));
        }
    }

    public void logHibernateVersion(String actualVersion) {
        info("org.hibernate.Version",
                String.format("HHH000412: Hibernate ORM core version %s", actualVersion));
    }

    public void logHibernateCacheDisabled() {
        info("o.h.c.internal.RegionFactoryInitiator", "HHH000026: Second-level cache disabled");
    }

    public void logNoLoadTimeWeaver() {
        info("o.s.o.j.p.SpringPersistenceUnitInfo", "No LoadTimeWeaver setup: ignoring JPA class transformer");
    }

    public void logDataSourceStarting() {
        info("com.zaxxer.hikari.HikariDataSource", "HikariPool-1 - Starting...");
    }

    public void logDataSourceConnection(String connectionDetails) {
        info("com.zaxxer.hikari.pool.HikariPool",
                String.format("HikariPool-1 - Added connection %s", connectionDetails));
    }

    public void logDataSourceStarted() {
        info("com.zaxxer.hikari.HikariDataSource", "HikariPool-1 - Start completed.");
    }

    public void logHibernateWarnings() {
        warn("org.hibernate.orm.deprecation",
                "HHH90000025: MySQL8Dialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)");
        warn("org.hibernate.orm.deprecation",
                "HHH90000026: MySQL8Dialect has been deprecated; use org.hibernate.dialect.MySQLDialect instead");
    }

    public void logNoJtaPlatform() {
        info("o.h.e.t.j.p.i.JtaPlatformInitiator",
                "HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)");
    }

    public void logEntityManagerFactoryInitialized() {
        info("j.LocalContainerEntityManagerFactoryBean",
                "Initialized JPA EntityManagerFactory for persistence unit 'default'");
    }

    public void logRepositoriesCreated(int actualRepoCount, long timeTaken, String[] repositoryNames) {
        info("c.k.f.data.RepositoryFactory",
                String.format("Finished creating %d repository proxies in %d ms: [%s]",
                        actualRepoCount, timeTaken, String.join(", ", repositoryNames)));
    }

    public void logBeansCreated(int actualBeanCount, String[] beanNames) {
        info("c.k.f.context.ApplicationContext",
                String.format("Created %d beans: [%s]", actualBeanCount, String.join(", ", beanNames)));
    }

    public void logTransactionalProxiesCreated(int actualProxyCount, String[] proxyNames) {
        if (actualProxyCount > 0) {
            info("c.k.f.transaction.ProxyFactory",
                    String.format("Created %d transactional proxies: [%s]",
                            actualProxyCount, String.join(", ", proxyNames)));
        }
    }

    public void logWebApplicationContextCompleted(long actualInitTime) {
        info("c.k.f.web.context.WebApplicationContext",
                String.format("Root WebApplicationContext: initialization completed in %d ms", actualInitTime));
    }

    public void logControllersRegistered(int actualControllerCount, int actualRouteCount, String[] controllerNames) {
        info("c.k.f.web.servlet.DispatcherServlet",
                String.format("Mapped %d handler methods from %d controllers: [%s]",
                        actualRouteCount, actualControllerCount, String.join(", ", controllerNames)));
    }

    public void logWebServerInitializing(int actualPort) {
        info("c.k.f.web.embedded.UndertowWebServer",
                String.format("Undertow initialized with port %d (http)", actualPort));
    }

    public void logWebServerStarting() {
        info("c.k.f.web.embedded.UndertowWebServer", "Starting service [Undertow]");
        info("c.k.f.web.embedded.UndertowWebServer", "Starting Servlet engine: [Undertow/2.3.10.Final]");
    }

    public void logWebServerStarted(int actualPort, double actualStartupTime, double actualJvmRuntime) {
        info("c.k.f.KorusApplication",
                String.format("Started KorusApplication in %.3f seconds (JVM running for %.3f)",
                        actualStartupTime, actualJvmRuntime));
        info("c.k.f.web.embedded.UndertowWebServer",
                String.format("Undertow started on port(s): %d (http) with context path ''", actualPort));
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime.get();
    }

    public double getElapsedTimeSeconds() {
        return getElapsedTime() / 1000.0;
    }
}
