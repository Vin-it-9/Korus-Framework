package com.korus.framework.transaction;

import com.korus.framework.annotations.Transactional;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class TransactionalInvocationHandler implements InvocationHandler {
    private final Object target;
    private final TransactionManager transactionManager;

    public TransactionalInvocationHandler(Object target, TransactionManager transactionManager) {
        this.target = target;
        this.transactionManager = transactionManager;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        Transactional methodTransactional = method.getAnnotation(Transactional.class);
        Transactional classTransactional = target.getClass().getAnnotation(Transactional.class);
        Transactional transactional = methodTransactional != null ? methodTransactional : classTransactional;

        if (transactional != null && !method.getDeclaringClass().equals(Object.class)) {
            return transactionManager.executeInTransaction(method, transactional, () -> {
                try {
                    return method.invoke(target, args);
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            });
        } else {
            return method.invoke(target, args);
        }
    }
}
