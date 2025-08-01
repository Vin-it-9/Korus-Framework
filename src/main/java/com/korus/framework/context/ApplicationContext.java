package com.korus.framework.context;

import com.korus.framework.annotations.*;
import com.korus.framework.console.Logger;
import com.korus.framework.data.JpaRepository;
import com.korus.framework.data.SimpleJpaRepository;
import com.korus.framework.transaction.ProxyFactory;
import com.korus.framework.transaction.TransactionManager;
import org.hibernate.*;
import org.hibernate.boot.*;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import org.reflections.Reflections;

import com.korus.framework.config.ConfigurationManager;

public class ApplicationContext {

    private final Map<Class<?>, Object> beans = new LinkedHashMap<>();
    private final Map<String, Object> namedBeans = new HashMap<>();
    private String basePackage;
    private SessionFactory sessionFactory;
    private final Map<String, Map<String, ControllerMethod>> routes = new HashMap<>();

    private final Logger logger = new Logger("korus-framework");

    private long startTime;
    private long configLoadTime;
    private long hibernateInitTime;
    private long beansCreatedTime;
    private long webContextInitTime;

    public void scan(String basePackage) {
        this.basePackage = basePackage;
    }

    public void start() throws Exception {
        printBanner();

        this.startTime = System.currentTimeMillis();

        String javaVersion = System.getProperty("java.version");
        logger.logApplicationStart(javaVersion);

        String activeProfile = System.getProperty("korus.profiles.active", "default");
        logger.logProfileActive(activeProfile);

        logger.logConfigurationStart();
        long configStartTime = System.currentTimeMillis();

        ConfigurationManager config = ConfigurationManager.getInstance();
        int actualPropertiesLoaded = config.getAllProperties().size();
        this.configLoadTime = System.currentTimeMillis() - configStartTime;
        logger.logConfigurationLoaded("application.properties", actualPropertiesLoaded);
        logger.logTotalPropertiesLoaded(actualPropertiesLoaded);

        Set<Class<?>> entities = scanEntities();
        String[] entityNames = entities.stream()
                .map(Class::getSimpleName)
                .toArray(String[]::new);
        logger.logEntitiesFound(entities.size(), entityNames);


        long hibernateStartTime = System.currentTimeMillis();

        logger.logHibernateCacheDisabled();
        logger.logNoLoadTimeWeaver();
        logger.logDataSourceStarting();

        initHibernate(entities);
        String hibernateVersion = org.hibernate.Version.getVersionString();
        logger.logHibernateVersion(hibernateVersion);

        logger.logDataSourceConnection("com.mysql.cj.jdbc.ConnectionImpl@" + Integer.toHexString(this.hashCode()));
        logger.logDataSourceStarted();

        logger.logHibernateWarnings();
        logger.logNoJtaPlatform();
        logger.logEntityManagerFactoryInitialized();

        this.hibernateInitTime = System.currentTimeMillis() - hibernateStartTime;

        long repoStartTime = System.currentTimeMillis();

        createRepositoryBeans();
        long repoCreationTime = System.currentTimeMillis() - repoStartTime;

        String[] repositoryNames = getActualRepositoryNames();
        logger.logRepositoriesCreated(repositoryNames.length, repoCreationTime, repositoryNames);

        long beanStartTime = System.currentTimeMillis();
        scanAndCreateBeans();
        this.beansCreatedTime = System.currentTimeMillis() - beanStartTime;

        String[] beanNames = getActualBeanNames();
        logger.logBeansCreated(beanNames.length, beanNames);

        createTransactionalProxies();
        String[] transactionalBeanNames = getActualTransactionalBeanNames();
        logger.logTransactionalProxiesCreated(transactionalBeanNames.length, transactionalBeanNames);
        int proxyCount = countTransactionalProxies();


        injectProperties();
        injectDependencies();

        long webContextStartTime = System.currentTimeMillis();
        this.webContextInitTime = System.currentTimeMillis() - webContextStartTime;
        logger.logWebApplicationContextCompleted(logger.getElapsedTime());


        scanControllers();
        String[] controllerNames = getActualControllerNames();
        int totalRoutes = getTotalRouteCount();
        logger.logControllersRegistered(controllerNames.length, totalRoutes, controllerNames);
        logger.info("c.k.f.KorusApplication", "Application startup completed");

        printRegisteredBeans();
    }

    private String[] getActualRepositoryNames() {
        return beans.keySet().stream()
                .filter(clazz -> clazz.getName().contains("Repository"))
                .map(Class::getSimpleName)
                .toArray(String[]::new);
    }

    private String[] getActualBeanNames() {
        return beans.keySet().stream()
                .filter(clazz -> !clazz.equals(SessionFactory.class))
                .filter(clazz -> !clazz.getName().contains("Repository"))
                .map(Class::getSimpleName)
                .toArray(String[]::new);
    }

    private String[] getActualTransactionalBeanNames() {
        return beans.keySet().stream()
                .filter(this::hasTransactionalAnnotation)
                .map(Class::getSimpleName)
                .toArray(String[]::new);
    }

    private String[] getActualControllerNames() {
        return beans.keySet().stream()
                .filter(clazz -> clazz.isAnnotationPresent(RestController.class) ||
                        clazz.isAnnotationPresent(Controller.class))
                .map(Class::getSimpleName)
                .toArray(String[]::new);
    }

    private int getTotalRouteCount() {
        return routes.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    private int countRepositoryBeans() {
        return (int) beans.keySet().stream()
                .filter(clazz -> clazz.getName().contains("Repository"))
                .count();
    }

    private int countTransactionalProxies() {
        return (int) beans.values().stream()
                .filter(bean -> hasTransactionalAnnotation(bean.getClass()))
                .count();
    }

    private int[] getControllerStats() {
        int controllerCount = (int) beans.keySet().stream()
                .filter(clazz -> clazz.isAnnotationPresent(RestController.class) ||
                        clazz.isAnnotationPresent(Controller.class))
                .count();

        int routeCount = routes.values().stream()
                .mapToInt(Map::size)
                .sum();

        return new int[]{controllerCount, routeCount};
    }

    private void printBanner() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("banner.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

        } catch (IOException | NullPointerException e) {
            System.out.println(":: Starting MyCustomJVM Framework ::");
        }
    }


    private void createTransactionalProxies() {

        TransactionManager transactionManager = new TransactionManager(sessionFactory);
        ProxyFactory proxyFactory = new ProxyFactory(transactionManager);
        Map<Class<?>, Object> transactionalBeans = new HashMap<>();

        for (Map.Entry<Class<?>, Object> entry : new HashMap<>(beans).entrySet()) {
            Class<?> beanClass = entry.getKey();
            Object beanInstance = entry.getValue();
            if (shouldSkipProxying(beanClass)) {
                continue;
            }
            if (hasTransactionalAnnotation(beanClass)) {
                Object proxy = proxyFactory.createProxy(beanInstance);
                transactionalBeans.put(beanClass, proxy);
            } else {
            }
        }

        beans.putAll(transactionalBeans);
        updateNamedBeans(transactionalBeans);

    }

    private boolean shouldSkipProxying(Class<?> beanClass) {
        return beanClass.equals(SessionFactory.class) ||
                beanClass.getName().startsWith("com.korus.framework.data") ||
                beanClass.getName().contains("Repository") ||
                beanClass.getName().startsWith("java.") ||
                beanClass.getName().startsWith("javax.") ||
                beanClass.getName().startsWith("jakarta.");
    }

    private boolean hasTransactionalAnnotation(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Transactional.class)) {
            return true;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Transactional.class)) {
                return true;
            }
        }

        return false;
    }

    private void updateNamedBeans(Map<Class<?>, Object> transactionalBeans) {
        for (Map.Entry<String, Object> entry : new HashMap<>(namedBeans).entrySet()) {
            Object bean = entry.getValue();
            for (Map.Entry<Class<?>, Object> proxyEntry : transactionalBeans.entrySet()) {
                if (proxyEntry.getKey().isInstance(bean)) {
                    namedBeans.put(entry.getKey(), proxyEntry.getValue());
                    break;
                }
            }
        }
    }

    private void initHibernate(Set<Class<?>> entities) {
        try {
            ConfigurationManager config = ConfigurationManager.getInstance();
            Properties hibernateProps = new Properties();

            Map<String, String> keyMap = Map.of(
                    "hibernate.connection.driver_class",
                    config.getProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver"),
                    "hibernate.connection.url", config.getProperty("hibernate.connection.url"),
                    "hibernate.connection.username", config.getProperty("hibernate.connection.username"),
                    "hibernate.connection.password", config.getProperty("hibernate.connection.password"),
                    "hibernate.dialect",
                    config.getProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect"),
                    "hibernate.hbm2ddl.auto", config.getProperty("hibernate.hbm2ddl.auto", "update"),
                    "hibernate.show_sql", config.getProperty("hibernate.show_sql", "false"),
                    "hibernate.format_sql", config.getProperty("hibernate.format_sql", "false"));

            hibernateProps.putAll(keyMap);

            StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder()
                    .applySettings(hibernateProps);
            MetadataSources metadataSources = new MetadataSources(registryBuilder.build());

            for (Class<?> entityClass : entities) {
                metadataSources.addAnnotatedClass(entityClass);
            }

            Metadata metadata = metadataSources.getMetadataBuilder().build();
            sessionFactory = metadata.getSessionFactoryBuilder().build();

            beans.put(SessionFactory.class, sessionFactory);
            namedBeans.put("sessionFactory", sessionFactory);

        } catch (Exception e) {
            logger.error("c.k.f.hibernate.HibernateInitializer",
                    "Failed to initialize Hibernate: " + e.getMessage());
            throw new RuntimeException("Failed to initialize Hibernate", e);
        }
    }

    private Set<Class<?>> scanEntities() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> entities = reflections.getTypesAnnotatedWith(jakarta.persistence.Entity.class);
        return entities;
    }

    private void createRepositoryBeans() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> repositoryClasses = reflections.getTypesAnnotatedWith(Repository.class);

        for (Class<?> repoClass : repositoryClasses) {
            if (repoClass.isInterface() && JpaRepository.class.isAssignableFrom(repoClass)) {
                try {
                    Type[] genericInterfaces = repoClass.getGenericInterfaces();
                    Class<?> entityClass = null;

                    for (Type genericInterface : genericInterfaces) {
                        if (genericInterface instanceof ParameterizedType) {
                            ParameterizedType paramType = (ParameterizedType) genericInterface;
                            Type rawType = paramType.getRawType();

                            if (rawType instanceof Class && JpaRepository.class.isAssignableFrom((Class<?>) rawType)) {
                                Type[] typeArgs = paramType.getActualTypeArguments();
                                if (typeArgs.length >= 1) {
                                    entityClass = (Class<?>) typeArgs[0];
                                    break;
                                }
                            }
                        }
                    }

                    if (entityClass != null) {
                        SimpleJpaRepository<?, ?> repoImpl = new SimpleJpaRepository<>(sessionFactory, entityClass);
                        Object proxyInstance = Proxy.newProxyInstance(
                                repoClass.getClassLoader(),
                                new Class[]{repoClass},
                                new RepositoryInvocationHandler(repoImpl, repoClass, sessionFactory, entityClass)
                        );

                        beans.put(repoClass, proxyInstance);
                        String beanName = getBeanName(repoClass);
                        namedBeans.put(beanName, proxyInstance);
                    }

                } catch (Exception e) {
                    System.err.println("Failed to create repository for " + repoClass.getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private static class RepositoryInvocationHandler implements InvocationHandler {
        private final Object target;
        private final Class<?> repositoryInterface;
        private final SessionFactory sessionFactory;
        private final Class<?> entityClass;

        public RepositoryInvocationHandler(Object target, Class<?> repositoryInterface, SessionFactory sessionFactory, Class<?> entityClass) {
            this.target = target;
            this.repositoryInterface = repositoryInterface;
            this.sessionFactory = sessionFactory;
            this.entityClass = entityClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            if (method.isAnnotationPresent(Query.class)) {
                return handleQueryAnnotation(method, args);
            }

            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException e) {
                return handleCustomQueryMethod(method, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    throw cause;
                } else {
                    throw e;
                }
            } catch (Exception e) {
                return handleCustomQueryMethod(method, args);
            }
        }

        private Object handleQueryAnnotation(Method method, Object[] args) {
            Query queryAnnotation = method.getAnnotation(Query.class);
            String queryString = queryAnnotation.value();
            boolean isNative = queryAnnotation.nativeQuery();

            try (Session session = sessionFactory.openSession()) {
                org.hibernate.query.Query query;

                if (isNative) {
                    query = session.createNativeQuery(queryString, entityClass);
                } else {
                    Class<?> returnType = getQueryReturnType(method);
                    query = session.createQuery(queryString, returnType);
                }

                setQueryParameters(query, method, args);
                applyPagination(query, method, args);
                return executeQuery(query, method);
            }
        }

        private void applyPagination(org.hibernate.query.Query query, Method method, Object[] args) {
            if (args == null || args.length == 0) {
                return;
            }

            java.lang.reflect.Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameters.length; i++) {
                if (isPagableParameter(parameters[i])) {
                    Object pageableArg = args[i];
                    if (pageableArg != null) {
                        try {
                            Method getOffsetMethod = pageableArg.getClass().getMethod("getOffset");
                            Method getPageSizeMethod = pageableArg.getClass().getMethod("getPageSize");

                            int offset = (Integer) getOffsetMethod.invoke(pageableArg);
                            int pageSize = (Integer) getPageSizeMethod.invoke(pageableArg);

                            query.setFirstResult(offset);
                            query.setMaxResults(pageSize);

                        } catch (Exception e) {
                            System.err.println("Failed to apply pagination: " + e.getMessage());
                        }
                    }
                    break;
                }
            }
        }

        private boolean isPagableParameter(java.lang.reflect.Parameter param) {
            return param.getType().getSimpleName().equals("Pageable") ||
                    param.getType().getName().contains("Pageable");
        }


        private void setQueryParameters(org.hibernate.query.Query query, Method method, Object[] args) {
            if (args == null || args.length == 0) {
                return;
            }
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            Query queryAnnotation = method.getAnnotation(Query.class);
            boolean isNative = queryAnnotation != null && queryAnnotation.nativeQuery();
            int paramIndex = 0;
            for (int i = 0; i < parameters.length; i++) {
                java.lang.reflect.Parameter param = parameters[i];
                if (isPagableParameter(param)) {
                    continue;
                }
                if (param.isAnnotationPresent(Param.class)) {
                    String paramName = param.getAnnotation(Param.class).value();
                    query.setParameter(paramName, args[i]);
                } else {
                    if (isNative) {
                        query.setParameter(paramIndex + 1, args[i]);
                    } else {
                        query.setParameter(paramIndex + 1, args[i]);
                    }
                    paramIndex++;
                }
            }
        }


        private Object executeQuery(org.hibernate.query.Query query, Method method) {
            Class<?> returnType = method.getReturnType();

            if (returnType.equals(Optional.class)) {
                Object result = query.uniqueResult();
                return Optional.ofNullable(result);
            } else if (List.class.isAssignableFrom(returnType)) {
                return query.list();
            } else if (returnType.equals(long.class) || returnType.equals(Long.class)) {
                Object result = query.uniqueResult();
                return result != null ? result : 0L;
            } else if (returnType.equals(int.class) || returnType.equals(Integer.class)) {
                Object result = query.uniqueResult();
                return result != null ? ((Number) result).intValue() : 0;
            } else if (returnType.equals(boolean.class) || returnType.equals(Boolean.class)) {
                Object result = query.uniqueResult();
                if (result instanceof Number) {
                    return ((Number) result).longValue() > 0;
                }
                return result != null;
            } else if (returnType.equals(void.class) || returnType.equals(Void.class)) {
                query.executeUpdate();
                return null;
            } else {
                return query.uniqueResult();
            }
        }

        private Class<?> getQueryReturnType(Method method) {
            Class<?> returnType = method.getReturnType();

            if (returnType.equals(Optional.class)) {
                Type genericReturnType = method.getGenericReturnType();
                if (genericReturnType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericReturnType;
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        return (Class<?>) typeArgs[0];
                    }
                }
                return entityClass;
            } else if (List.class.isAssignableFrom(returnType)) {
                Type genericReturnType = method.getGenericReturnType();
                if (genericReturnType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericReturnType;
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        return (Class<?>) typeArgs[0];
                    }
                }
                return entityClass;
            } else if (returnType.equals(long.class) || returnType.equals(Long.class) ||
                    returnType.equals(int.class) || returnType.equals(Integer.class)) {
                return Long.class;
            } else {
                return returnType.equals(void.class) || returnType.equals(Void.class) ? null : returnType;
            }
        }

        private Object handleCustomQueryMethod(Method method, Object[] args) throws Exception {
            String methodName = method.getName();

            if (methodName.startsWith("findBy")) {
                return handleFindByMethod(method, args);
            }
            if (methodName.startsWith("findAllBy")) {
                return handleFindAllByMethod(method, args);
            }

            if (methodName.startsWith("findFirst") || methodName.startsWith("findTop")) {
                return handleFindFirstTopMethod(method, args);
            }

            if (methodName.startsWith("countBy")) {
                return handleCountByMethod(method, args);
            }

            if (methodName.startsWith("existsBy")) {
                return handleExistsByMethod(method, args);
            }

            if (methodName.startsWith("deleteBy") || methodName.startsWith("removeBy")) {
                return handleDeleteByMethod(method, args);
            }

            throw new UnsupportedOperationException(
                    "Method " + methodName + " is not supported for " + repositoryInterface.getSimpleName()
            );
        }

        private Object handleFindByMethod(Method method, Object[] args) {
            String methodName = method.getName();
            String queryPart = methodName.substring(6);
            String hql = buildQueryFromMethodName(queryPart, args, "select");

            try (Session session = sessionFactory.openSession()) {
                org.hibernate.query.Query query = session.createQuery(hql, entityClass);
                setParametersFromMethodName(query, queryPart, args);

                if (method.getReturnType().equals(Optional.class)) {
                    Object result = query.uniqueResult();
                    return Optional.ofNullable(result);
                } else if (List.class.isAssignableFrom(method.getReturnType())) {
                    return query.list();
                } else {
                    return query.uniqueResult();
                }
            }
        }

        private Object handleFindAllByMethod(Method method, Object[] args) {
            String methodName = method.getName();
            String queryPart = methodName.substring(9);
            String hql = buildQueryFromMethodName(queryPart, args, "select");

            try (Session session = sessionFactory.openSession()) {
                org.hibernate.query.Query query = session.createQuery(hql, entityClass);
                setParametersFromMethodName(query, queryPart, args);
                return query.list();
            }
        }

        private Object handleFindFirstTopMethod(Method method, Object[] args) {
            String methodName = method.getName();
            String queryPart;
            int limit = 1;

            if (methodName.startsWith("findFirst")) {
                queryPart = methodName.substring(9);
                if (queryPart.matches("^\\d+.*")) {
                    String numberPart = queryPart.replaceAll("^(\\d+).*", "$1");
                    limit = Integer.parseInt(numberPart);
                    queryPart = queryPart.substring(numberPart.length());
                }
            } else {
                queryPart = methodName.substring(7);
                if (queryPart.matches("^\\d+.*")) {
                    String numberPart = queryPart.replaceAll("^(\\d+).*", "$1");
                    limit = Integer.parseInt(numberPart);
                    queryPart = queryPart.substring(numberPart.length());
                }
            }

            if (queryPart.startsWith("ByOrderBy") || queryPart.equals("ByOrderByNameAsc") || queryPart.equals("ByOrderByNameDesc")) {
                return handleOrderByMethod(method, args, limit, queryPart);
            }

            if (queryPart.startsWith("By")) {
                queryPart = queryPart.substring(2);
            }

            if (queryPart.isEmpty()) {
                try (Session session = sessionFactory.openSession()) {
                    String hql = "from " + entityClass.getName();
                    org.hibernate.query.Query query = session.createQuery(hql, entityClass);
                    query.setMaxResults(limit);

                    List results = query.list();
                    if (method.getReturnType().equals(Optional.class)) {
                        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
                    } else if (List.class.isAssignableFrom(method.getReturnType())) {
                        return results;
                    } else {
                        return results.isEmpty() ? null : results.get(0);
                    }
                }
            }

            String hql = buildQueryFromMethodName(queryPart, args, "select");

            try (Session session = sessionFactory.openSession()) {
                org.hibernate.query.Query query = session.createQuery(hql, entityClass);
                setParametersFromMethodName(query, queryPart, args);
                query.setMaxResults(limit);

                List results = query.list();
                if (method.getReturnType().equals(Optional.class)) {
                    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
                } else if (List.class.isAssignableFrom(method.getReturnType())) {
                    return results;
                } else {
                    return results.isEmpty() ? null : results.get(0);
                }
            }
        }

        private Object handleOrderByMethod(Method method, Object[] args, int limit, String queryPart) {
            try (Session session = sessionFactory.openSession()) {
                String hql;

                if (queryPart.equals("ByOrderByNameAsc") || queryPart.startsWith("ByOrderByNameAsc")) {
                    hql = "from " + entityClass.getName() + " e order by e.name asc";
                } else if (queryPart.equals("ByOrderByNameDesc") || queryPart.startsWith("ByOrderByNameDesc")) {
                    hql = "from " + entityClass.getName() + " e order by e.name desc";
                } else if (queryPart.startsWith("ByOrderBy")) {
                    String orderPart = queryPart.substring(9);
                    String property = "name";
                    String direction = "asc";

                    if (orderPart.endsWith("Asc")) {
                        property = orderPart.substring(0, orderPart.length() - 3);
                        direction = "asc";
                    } else if (orderPart.endsWith("Desc")) {
                        property = orderPart.substring(0, orderPart.length() - 4);
                        direction = "desc";
                    } else {
                        property = orderPart;
                    }

                    String camelCaseProperty = toCamelCase(property);
                    hql = "from " + entityClass.getName() + " e order by e." + camelCaseProperty + " " + direction;
                } else {
                    hql = "from " + entityClass.getName();
                }

                org.hibernate.query.Query query = session.createQuery(hql, entityClass);
                query.setMaxResults(limit);

                List results = query.list();
                if (method.getReturnType().equals(Optional.class)) {
                    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
                } else if (List.class.isAssignableFrom(method.getReturnType())) {
                    return results;
                } else {
                    return results.isEmpty() ? null : results.get(0);
                }
            }
        }



        private Object handleCountByMethod(Method method, Object[] args) {
            String methodName = method.getName();
            String queryPart = methodName.substring(7);
            String hql = buildQueryFromMethodName(queryPart, args, "count");

            try (Session session = sessionFactory.openSession()) {
                org.hibernate.query.Query<Long> query = session.createQuery(hql, Long.class);
                setParametersFromMethodName(query, queryPart, args);
                Long count = query.uniqueResult();
                return count != null ? count : 0L;
            }
        }

        private Object handleExistsByMethod(Method method, Object[] args) {
            String methodName = method.getName();
            String queryPart = methodName.substring(8);
            String hql = buildQueryFromMethodName(queryPart, args, "count");

            try (Session session = sessionFactory.openSession()) {
                org.hibernate.query.Query<Long> query = session.createQuery(hql, Long.class);
                setParametersFromMethodName(query, queryPart, args);
                Long count = query.uniqueResult();
                return count != null && count > 0;
            }
        }

        private Object handleDeleteByMethod(Method method, Object[] args) {
            String methodName = method.getName();
            String queryPart;

            if (methodName.startsWith("deleteBy")) {
                queryPart = methodName.substring(8);
            } else {
                queryPart = methodName.substring(8);
            }

            String hql = buildQueryFromMethodName(queryPart, args, "delete");

            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();
                org.hibernate.query.Query query = session.createQuery(hql);
                setParametersFromMethodName(query, queryPart, args);
                int deletedCount = query.executeUpdate();
                session.getTransaction().commit();

                if (method.getReturnType().equals(void.class) || method.getReturnType().equals(Void.class)) {
                    return null;
                } else {
                    return deletedCount;
                }
            }
        }

        private String buildQueryFromMethodName(String queryPart, Object[] args, String operation) {
            if (queryPart.isEmpty()) {
                if ("select".equals(operation)) {
                    return "from " + entityClass.getName();
                } else if ("count".equals(operation)) {
                    return "select count(e) from " + entityClass.getName() + " e";
                } else if ("delete".equals(operation)) {
                    return "delete from " + entityClass.getName();
                }
            }

            StringBuilder hql = new StringBuilder();

            if ("select".equals(operation)) {
                hql.append("from ").append(entityClass.getName()).append(" e where ");
            } else if ("count".equals(operation)) {
                hql.append("select count(e) from ").append(entityClass.getName()).append(" e where ");
            } else if ("delete".equals(operation)) {
                hql.append("delete from ").append(entityClass.getName()).append(" e where ");
            }

            String[] parts = queryPart.split("And|Or");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    if (queryPart.contains("And")) {
                        hql.append(" and ");
                    } else {
                        hql.append(" or ");
                    }
                }

                String part = parts[i];
                String propertyName = getPropertyName(part);
                String operator = getOperator(part);

                hql.append("e.").append(propertyName).append(" ").append(operator).append(" :param").append(i);
            }

            return hql.toString();
        }

        private void setParametersFromMethodName(org.hibernate.query.Query query, String queryPart, Object[] args) {
            if (queryPart.isEmpty()) return;

            String[] parts = queryPart.split("And|Or");
            for (int i = 0; i < parts.length && i < args.length; i++) {
                query.setParameter("param" + i, args[i]);
            }
        }

        private String getPropertyName(String part) {
            if (part == null || part.isEmpty()) {
                return "";
            }
            if (part.endsWith("GreaterThan")) {
                return toCamelCase(part.substring(0, part.length() - 11));
            } else if (part.endsWith("LessThan")) {
                return toCamelCase(part.substring(0, part.length() - 8));
            } else if (part.endsWith("Containing")) {
                return toCamelCase(part.substring(0, part.length() - 10));
            } else if (part.endsWith("StartingWith")) {
                return toCamelCase(part.substring(0, part.length() - 12));
            } else if (part.endsWith("EndingWith")) {
                return toCamelCase(part.substring(0, part.length() - 10));
            } else if (part.endsWith("IgnoreCase")) {
                return toCamelCase(part.substring(0, part.length() - 10));
            } else {
                return toCamelCase(part);
            }
        }


        private String getOperator(String part) {
            if (part.endsWith("GreaterThan")) {
                return ">";
            } else if (part.endsWith("LessThan")) {
                return "<";
            } else if (part.endsWith("Containing")) {
                return "like";
            } else if (part.endsWith("StartingWith")) {
                return "like";
            } else if (part.endsWith("EndingWith")) {
                return "like";
            } else {
                return "=";
            }
        }

        private String toCamelCase(String input) {
            if (input == null || input.isEmpty()) {
                return input;
            }
            return Character.toLowerCase(input.charAt(0)) + input.substring(1);
        }

    }


    private void scanAndCreateBeans() throws Exception {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> componentClasses = new LinkedHashSet<>();
        componentClasses.addAll(reflections.getTypesAnnotatedWith(Component.class));
        componentClasses.addAll(reflections.getTypesAnnotatedWith(Service.class));
        componentClasses.addAll(reflections.getTypesAnnotatedWith(Controller.class));
        componentClasses.addAll(reflections.getTypesAnnotatedWith(RestController.class));

        Set<Class<?>> repositoryClasses = reflections.getTypesAnnotatedWith(Repository.class);
        for (Class<?> repoClass : repositoryClasses) {
            if (!repoClass.isInterface()) {
                componentClasses.add(repoClass);
            }
        }

        Set<Class<?>> remaining = new LinkedHashSet<>(componentClasses);
        while (!remaining.isEmpty()) {
            int before = remaining.size();
            Iterator<Class<?>> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                Class<?> clazz = iterator.next();
                Object instance = createBeanWithDependencies(clazz);
                if (instance != null) {
                    beans.put(clazz, instance);
                    String beanName = getBeanName(clazz);
                    namedBeans.put(beanName, instance);
                    iterator.remove();
                }
            }
            if (remaining.size() == before) {
                throw new RuntimeException("Circular dependencies or missing dependencies detected among: " + remaining);
            }
        }
    }

    private String getBeanName(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation a : annotations) {
            try {
                Method valueMethod = a.annotationType().getMethod("value");
                String name = (String) valueMethod.invoke(a);
                if (!name.isEmpty()) return name;
            } catch (Exception ignored) {}
        }
        String className = clazz.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    private Object createBeanWithDependencies(Class<?> clazz) throws Exception {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        for (Constructor<?> ctor : constructors) {
            if (ctor.isAnnotationPresent(Autowired.class)) {
                Object instance = tryCreateWithConstructor(ctor);
                if (instance != null) return instance;
            }
        }
        for (Constructor<?> ctor : constructors) {
            Object instance = tryCreateWithConstructor(ctor);
            if (instance != null) return instance;
        }
        try {
            Constructor<?> defaultCtor = clazz.getDeclaredConstructor();
            defaultCtor.setAccessible(true);
            return defaultCtor.newInstance();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Object tryCreateWithConstructor(Constructor<?> ctor) throws Exception {
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Object paramObj = beans.get(paramTypes[i]);
            if (paramObj == null) {
                return null;
            }
            params[i] = paramObj;
        }
        ctor.setAccessible(true);
        return ctor.newInstance(params);
    }

    private void injectProperties() {
        ConfigurationManager config = ConfigurationManager.getInstance();
        for (Object bean : beans.values()) {
            Class<?> clazz = bean.getClass();
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    if (field.isAnnotationPresent(Value.class)) {
                        String expression = field.getAnnotation(Value.class).value();
                        String value = config.resolveValue(expression);
                        if (value != null) {
                            field.setAccessible(true);
                            field.set(bean, convertValue(field.getType(), value));
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to inject @Value into " + clazz.getSimpleName() + "." + field.getName() + ": " + ex.getMessage());
                }
            }
            if (clazz.isAnnotationPresent(ConfigurationProperties.class)) {
                String prefix = clazz.getAnnotation(ConfigurationProperties.class).prefix();
                Map<String, String> props = config.getPropertiesWithPrefix(prefix);
                for (Map.Entry<String, String> entry : props.entrySet()) {
                    try {
                        Field f = clazz.getDeclaredField(entry.getKey());
                        f.setAccessible(true);
                        f.set(bean, convertValue(f.getType(), entry.getValue()));
                    } catch (NoSuchFieldException ignored) {
                    } catch (Exception e) {
                        System.err.println("Failed to inject @ConfigurationProperties: " + e.getMessage());
                    }
                }
            }
        }
    }




    private Object convertValue(Class<?> targetType, String value) {
        if (targetType.equals(String.class)) return value;
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) return Integer.parseInt(value);
        if (targetType.equals(Long.class) || targetType.equals(long.class)) return Long.parseLong(value);
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) return Boolean.parseBoolean(value);
        if (targetType.equals(Double.class) || targetType.equals(double.class)) return Double.parseDouble(value);
        return value;
    }

    private void injectDependencies() throws Exception {
        for (Object bean : beans.values()) {
            Class<?> clazz = bean.getClass();
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    field.setAccessible(true);
                    Object dependency = beans.get(field.getType());
                    if (dependency == null) {
                        throw new RuntimeException("Unsatisfied dependency for " + field.getType() + " in " + clazz);
                    }
                    field.set(bean, dependency);
                }
            }
        }
    }

    private void scanControllers() {
        Reflections reflections = new Reflections(basePackage);
        routes.clear();
        Set<Class<?>> restControllers = reflections.getTypesAnnotatedWith(RestController.class);
        for (Class<?> c : restControllers) {
            addRoutesForController(c);
        }
        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(Controller.class);
        for (Class<?> c : controllers) {
            addRoutesForController(c);
        }
    }

    private void addRoutesForController(Class<?> controllerClass) {
        Object controllerInstance = beans.get(controllerClass);
        if (controllerInstance == null) return;
        for (Method method : controllerClass.getDeclaredMethods()) {
            mapRoute(controllerInstance, method, GetMapping.class, "GET");
            mapRoute(controllerInstance, method, PostMapping.class, "POST");
            mapRoute(controllerInstance, method, PutMapping.class, "PUT");
            mapRoute(controllerInstance, method, DeleteMapping.class, "DELETE");
        }
    }

    private void mapRoute(Object controller, Method method, Class<? extends Annotation> annClass, String httpMethod) {
        if (!method.isAnnotationPresent(annClass)) return;
        try {
            String path = (String) method.getAnnotation(annClass).annotationType().getMethod("value").invoke(method.getAnnotation(annClass));
            routes.computeIfAbsent(path, k -> new HashMap<>()).put(httpMethod, new ControllerMethod(controller, method));
        } catch(Exception e) {
            System.err.println("Failed to map route: " + e.getMessage());
        }
    }

    public <T> T getBean(Class<T> clazz) {
        Object bean = beans.get(clazz);
        if (bean == null) {
            throw new RuntimeException("No bean found for " + clazz.getName());
        }
        return clazz.cast(bean);
    }

    public Object getBean(String name) {
        return namedBeans.get(name);
    }

    public void reload(String packageName) {
        System.out.println("Clearing old beans...");
        beans.clear();
        namedBeans.clear();
        routes.clear();
        this.basePackage = packageName;
        try {
            start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload application context", e);
        }
        System.out.println("Application context reloaded with " + beans.size() + " beans");
    }

    private void printRegisteredBeans() {
        beans.forEach((k,v) -> System.out.println(" - " + k.getSimpleName()));
    }

    public Map<String, Map<String, ControllerMethod>> getRoutes() {
        return routes;
    }

    public static class ControllerMethod {
        private final Object controller;
        private final Method method;

        public ControllerMethod(Object controller, Method method) {
            this.controller = controller;
            this.method = method;
        }

        public Object getController() { return controller; }
        public Method getMethod() { return method; }
    }
}
