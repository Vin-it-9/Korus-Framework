package com.korus.framework.data;

public class Pageable {
    private int pageNumber;
    private int pageSize;

    public Pageable(int pageNumber, int pageSize) {
        if (pageNumber < 0) throw new IllegalArgumentException("Page index must not be less than zero!");
        if (pageSize < 1) throw new IllegalArgumentException("Page size must not be less than one!");
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }

    public int getOffset() {
        return pageNumber * pageSize;
    }
}
