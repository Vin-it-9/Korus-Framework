package com.korus.framework.transaction;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import com.korus.framework.annotations.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class ProxyFactory {

    private final TransactionManager transactionManager;
    private final Objenesis objenesis = new ObjenesisStd();


    public ProxyFactory(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public Object createProxy(Object target) {
        Class<?> targetClass = target.getClass();

        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(targetClass);
            enhancer.setCallbackType(MethodInterceptor.class);
            Class<?> proxyClass = enhancer.createClass();
            ObjectInstantiator<?> instantiator = objenesis.getInstantiatorOf(proxyClass);
            Object proxyInstance = instantiator.newInstance();
            ((org.springframework.cglib.proxy.Factory) proxyInstance)
                    .setCallback(0, new TransactionalMethodInterceptor(target, transactionManager));

            copyFields(target, proxyInstance);
            return proxyInstance;

        } catch (Exception e) {
            return target;
        }
    }


    private Object createProxyWithConstructorArgs(Object target, Class<?> targetClass, Exception originalException) {
        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(targetClass);
            enhancer.setCallback(new TransactionalMethodInterceptor(target, transactionManager));
            Object[] constructorArgs = extractConstructorArgs(target);
            Object proxyInstance = enhancer.create(getConstructorArgTypes(constructorArgs), constructorArgs);

            copyFields(target, proxyInstance);
            return proxyInstance;

        } catch (Exception e2) {
            return target;
        }
    }

    private Object[] extractConstructorArgs(Object target) {

        Field[] fields = target.getClass().getDeclaredFields();
        java.util.List<Object> args = new java.util.ArrayList<>();

        for (Field field : fields) {
            if (field.getType().getName().contains("Repository") ||
                    field.getType().getName().contains("Service")) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null) {
                        args.add(value);
                    }
                } catch (Exception e) {

                }
            }
        }

        return args.toArray();
    }

    private Class<?>[] getConstructorArgTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
    }

    private void copyFields(Object source, Object target) {
        Class<?> clazz = source.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(source);
                    field.set(target, value);
                } catch (Exception e) {

                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static class TransactionalMethodInterceptor implements MethodInterceptor {

        private final Object target;
        private final TransactionManager transactionManager;

        public TransactionalMethodInterceptor(Object target, TransactionManager transactionManager) {
            this.target = target;
            this.transactionManager = transactionManager;
        }

        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            if (method.getDeclaringClass().equals(Object.class)) {
                return proxy.invokeSuper(obj, args);
            }

            Transactional transactional = method.getAnnotation(Transactional.class);
            if (transactional == null) {
                transactional = target.getClass().getAnnotation(Transactional.class);
            }

            if (transactional != null) {
                return transactionManager.executeInTransaction(method, transactional, () -> {
                    try {
                        return proxy.invokeSuper(obj, args);
                    } catch (Throwable t) {
                        if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        } else {
                            throw new RuntimeException(t);
                        }
                    }
                });
            } else {
                return proxy.invokeSuper(obj, args);
            }
        }
    }
}
