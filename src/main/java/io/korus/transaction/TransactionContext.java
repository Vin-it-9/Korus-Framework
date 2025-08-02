package io.korus.transaction;

import org.hibernate.*;

import java.util.Stack;

public class TransactionContext {
    private static final ThreadLocal<Stack<TransactionInfo>> transactionStack =
            new ThreadLocal<Stack<TransactionInfo>>() {
                @Override
                protected Stack<TransactionInfo> initialValue() {
                    return new Stack<>();
                }
            };

    public static void pushTransaction(TransactionInfo transactionInfo) {
        transactionStack.get().push(transactionInfo);
    }

    public static TransactionInfo popTransaction() {
        Stack<TransactionInfo> stack = transactionStack.get();
        if (!stack.isEmpty()) {
            return stack.pop();
        }
        return null;
    }

    public static TransactionInfo getCurrentTransaction() {
        Stack<TransactionInfo> stack = transactionStack.get();
        if (!stack.isEmpty()) {
            return stack.peek();
        }
        return null;
    }

    public static boolean hasActiveTransaction() {
        return getCurrentTransaction() != null;
    }

    public static void clearTransactionContext() {
        transactionStack.remove();
    }

    public static class TransactionInfo {
        private final Session session;
        private final Transaction transaction;
        private final boolean readOnly;
        private final int timeout;
        private boolean rollbackOnly = false;

        public TransactionInfo(Session session, Transaction transaction, boolean readOnly, int timeout) {
            this.session = session;
            this.transaction = transaction;
            this.readOnly = readOnly;
            this.timeout = timeout;
        }

        public Session getSession() { return session; }
        public Transaction getTransaction() { return transaction; }
        public boolean isReadOnly() { return readOnly; }
        public int getTimeout() { return timeout; }
        public boolean isRollbackOnly() { return rollbackOnly; }
        public void setRollbackOnly() { this.rollbackOnly = true; }
    }
}
