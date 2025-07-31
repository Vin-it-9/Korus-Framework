package com.korus.framework.data;

import java.util.List;

public interface PagingAndSortingRepository<T, ID> extends CrudRepository<T, ID> {
    List<T> findAll(Pageable pageable);
    List<T> findAll(Pageable pageable, Sort sort);
    List<T> findAll(Sort sort);
}
