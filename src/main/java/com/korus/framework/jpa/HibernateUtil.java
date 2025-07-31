package com.korus.framework.jpa;

import com.korus.framework.PropertiesLoader;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.Metadata;
import org.hibernate.SessionFactory;

import java.util.Properties;

import java.util.Set;

public class HibernateUtil {
    private static SessionFactory sessionFactory;

    public static void init(Set<Class<?>> entityClasses) {
        if (sessionFactory != null) return;

        Properties hibernateProps = PropertiesLoader.load("application.properties");
        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder().applySettings(hibernateProps);

        MetadataSources sources = new MetadataSources(registryBuilder.build());
        for (Class<?> entity : entityClasses) {
            sources.addAnnotatedClass(entity);
        }
        Metadata metadata = sources.getMetadataBuilder().build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
        System.out.println("✅ Hibernate SessionFactory initialized.");
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) throw new IllegalStateException("Call init() with entities first");
        return sessionFactory;
    }
}
