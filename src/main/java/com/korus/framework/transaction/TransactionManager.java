package com.korus.framework.transaction;

import com.korus.framework.annotations.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.lang.reflect.Method;

public class TransactionManager {
    private final SessionFactory sessionFactory;

    public TransactionManager(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Object executeInTransaction(Method method, Transactional transactional,
                                       TransactionalCallback callback) throws Throwable {

        Propagation propagation = transactional.propagation();
        boolean readOnly = transactional.readOnly();
        int timeout = transactional.timeout();

        TransactionContext.TransactionInfo existingTx = TransactionContext.getCurrentTransaction();

        switch (propagation) {
            case REQUIRED:
                if (existingTx != null) {
                    return executeWithExistingTransaction(callback, existingTx, transactional);
                } else {
                    return executeWithNewTransaction(callback, transactional, readOnly, timeout);
                }

            case REQUIRES_NEW:
                return executeWithNewTransaction(callback, transactional, readOnly, timeout);

            case SUPPORTS:
                if (existingTx != null) {
                    return executeWithExistingTransaction(callback, existingTx, transactional);
                } else {
                    return callback.execute();
                }

            case NOT_SUPPORTED:
                return callback.execute();

            case NEVER:
                if (existingTx != null) {
                    throw new RuntimeException("Existing transaction found when NEVER propagation was specified");
                }
                return callback.execute();

            case MANDATORY:
                if (existingTx == null) {
                    throw new RuntimeException("No existing transaction found when MANDATORY propagation was specified");
                }
                return executeWithExistingTransaction(callback, existingTx, transactional);

            default:
                return executeWithNewTransaction(callback, transactional, readOnly, timeout);
        }
    }

    private Object executeWithNewTransaction(TransactionalCallback callback,
                                             Transactional transactional,
                                             boolean readOnly, int timeout) throws Throwable {
        Session session = sessionFactory.openSession();
        Transaction transaction = null;
        TransactionContext.TransactionInfo txInfo = null;

        try {
            transaction = session.beginTransaction();
            applyIsolationLevel(session, transactional.isolation());
            if (timeout > 0) {
                transaction.setTimeout(timeout);
            }

            txInfo = new TransactionContext.TransactionInfo(session, transaction, readOnly, timeout);
            TransactionContext.pushTransaction(txInfo);
            Object result = callback.execute();
            if (txInfo.isRollbackOnly()) {
                transaction.rollback();
                System.out.println("🔄 Transaction rolled back (rollback-only)");
            } else {
                transaction.commit();
                System.out.println("✅ Transaction committed successfully");
            }

            return result;

        } catch (Throwable ex) {
            System.out.println("❌ Exception in transaction: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());

            if (transaction != null && transaction.isActive()) {
                if (shouldRollback(ex, transactional)) {
                    transaction.rollback();
                    System.out.println("🔄 Transaction rolled back due to exception");
                } else {
                    transaction.commit();
                    System.out.println("✅ Transaction committed despite exception (noRollbackFor rule)");
                }
            }
            throw ex;

        } finally {
            TransactionContext.popTransaction();
            if (session != null) {
                session.close();
            }
        }
    }

    private Object executeWithExistingTransaction(TransactionalCallback callback,
                                                  TransactionContext.TransactionInfo existingTx,
                                                  Transactional transactional) throws Throwable {
        try {
            return callback.execute();
        } catch (Throwable ex) {
            if (shouldRollback(ex, transactional)) {
                existingTx.setRollbackOnly();
                System.out.println("🔄 Marked existing transaction for rollback");
            }
            throw ex;
        }
    }

    private boolean shouldRollback(Throwable ex, Transactional transactional) {
        // Check noRollbackFor exceptions first
        for (Class<? extends Throwable> noRollbackException : transactional.noRollbackFor()) {
            if (noRollbackException.isAssignableFrom(ex.getClass())) {
                return false;
            }
        }

        // Check rollbackFor exceptions
        if (transactional.rollbackFor().length > 0) {
            for (Class<? extends Throwable> rollbackException : transactional.rollbackFor()) {
                if (rollbackException.isAssignableFrom(ex.getClass())) {
                    return true;
                }
            }
            return false;
        }

        // Default behavior: rollback on RuntimeException and Error, not on checked exceptions
        return (ex instanceof RuntimeException) || (ex instanceof Error);
    }

    private void applyIsolationLevel(Session session, Isolation isolation) {
        if (isolation != Isolation.DEFAULT) {
            int hibernateIsolation = mapIsolationLevel(isolation);
            session.doWork(connection -> {
                try {
                    connection.setTransactionIsolation(hibernateIsolation);
                } catch (Exception e) {
                    System.err.println("Failed to set isolation level: " + e.getMessage());
                }
            });
        }
    }

    private int mapIsolationLevel(Isolation isolation) {
        switch (isolation) {
            case READ_UNCOMMITTED: return java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
            case READ_COMMITTED: return java.sql.Connection.TRANSACTION_READ_COMMITTED;
            case REPEATABLE_READ: return java.sql.Connection.TRANSACTION_REPEATABLE_READ;
            case SERIALIZABLE: return java.sql.Connection.TRANSACTION_SERIALIZABLE;
            default: return java.sql.Connection.TRANSACTION_READ_COMMITTED;
        }
    }

    @FunctionalInterface
    public interface TransactionalCallback {
        Object execute() throws Throwable;
    }
}
