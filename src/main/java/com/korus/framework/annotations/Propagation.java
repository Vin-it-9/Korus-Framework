package com.korus.framework.annotations;

public enum Propagation {
    REQUIRED,       // Join existing transaction or create new one
    REQUIRES_NEW,   // Always create new transaction (suspend current)
    SUPPORTS,       // Join existing transaction or run non-transactionally
    NOT_SUPPORTED,  // Always run non-transactionally (suspend current)
    NEVER,          // Fail if transaction exists
    MANDATORY       // Fail if no transaction exists
}
