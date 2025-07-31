package com.korus.framework.annotations;

public enum Isolation {
    DEFAULT,
    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}
