package io.korus.transaction.annotation;

public enum Propagation {
    REQUIRED,
    REQUIRES_NEW,
    SUPPORTS,
    NOT_SUPPORTED,
    NEVER,
    MANDATORY
}
