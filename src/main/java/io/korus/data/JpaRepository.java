package io.korus.data;

import java.util.*;

public interface JpaRepository<T, ID> extends PagingAndSortingRepository<T, ID> {

    void flush();
    <S extends T> S saveAndFlush(S entity);
    <S extends T> List<S> saveAllAndFlush(Iterable<S> entities);
    void deleteAllInBatch();
    void deleteAllInBatch(Iterable<T> entities);
    void deleteAllByIdInBatch(Iterable<ID> ids);
    @Deprecated
    T getOne(ID id);
    T getById(ID id);
    T getReferenceById(ID id);
    @Override
    List<T> findAll();
    @Override
    List<T> findAllById(Iterable<ID> ids);
}
