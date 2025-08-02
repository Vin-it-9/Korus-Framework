package io.korus.data;

import io.korus.transaction.TransactionContext;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimpleJpaRepository<T, ID> implements JpaRepository<T, ID> {

    protected final SessionFactory sessionFactory;
    private final Class<T> entityClass;

    public SimpleJpaRepository(SessionFactory sessionFactory, Class<T> entityClass) {
        this.sessionFactory = sessionFactory;
        this.entityClass = entityClass;
    }


    @Override
    public <S extends T> S save(S entity) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            session.saveOrUpdate(entity);
            return entity;
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                session.saveOrUpdate(entity);
                tx.commit();
                return entity;
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }


    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            List<S> savedEntities = new ArrayList<>();
            for (S entity : entities) {
                session.saveOrUpdate(entity);
                savedEntities.add(entity);
            }
            return savedEntities;
        } else {
            List<S> savedEntities = new ArrayList<>();
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                for (S entity : entities) {
                    session.saveOrUpdate(entity);
                    savedEntities.add(entity);
                }
                tx.commit();
                return savedEntities;
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public <S extends T> S saveAndFlush(S entity) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            session.saveOrUpdate(entity);
            session.flush();
            return entity;
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                session.saveOrUpdate(entity);
                session.flush();
                tx.commit();
                return entity;
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            List<S> savedEntities = new ArrayList<>();
            for (S entity : entities) {
                session.saveOrUpdate(entity);
                savedEntities.add(entity);
            }
            session.flush();
            return savedEntities;
        } else {
            List<S> savedEntities = new ArrayList<>();
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                for (S entity : entities) {
                    session.saveOrUpdate(entity);
                    savedEntities.add(entity);
                }
                session.flush();
                tx.commit();
                return savedEntities;
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public Optional<T> findById(ID id) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            T entity = session.get(entityClass, (java.io.Serializable) id);
            return Optional.ofNullable(entity);
        } else {
            try (Session session = sessionFactory.openSession()) {
                T entity = session.get(entityClass, (java.io.Serializable) id);
                return Optional.ofNullable(entity);
            }
        }
    }

    @Override
    public T getById(ID id) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            T entity = session.get(entityClass, (java.io.Serializable) id);
            if (entity == null) {
                throw new EntityNotFoundException("Entity with id " + id + " not found");
            }
            return entity;
        } else {
            try (Session session = sessionFactory.openSession()) {
                T entity = session.get(entityClass, (java.io.Serializable) id);
                if (entity == null) {
                    throw new EntityNotFoundException("Entity with id " + id + " not found");
                }
                return entity;
            }
        }
    }

    @Override
    public T getReferenceById(ID id) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            return session.getReference(entityClass, (java.io.Serializable) id);
        } else {
            try (Session session = sessionFactory.openSession()) {
                return session.getReference(entityClass, (java.io.Serializable) id);
            }
        }
    }

    @Override
    @Deprecated
    public T getOne(ID id) {
        return getReferenceById(id);
    }

    @Override
    public List<T> findAll() {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            return session.createQuery("from " + entityClass.getName(), entityClass).list();
        } else {
            try (Session session = sessionFactory.openSession()) {
                return session.createQuery("from " + entityClass.getName(), entityClass).list();
            }
        }
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        List<ID> idList = new ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) return new ArrayList<>();

        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            String hql = "from " + entityClass.getName() + " e where e.id in :ids";
            return session.createQuery(hql, entityClass)
                    .setParameterList("ids", idList)
                    .list();
        } else {
            try (Session session = sessionFactory.openSession()) {
                String hql = "from " + entityClass.getName() + " e where e.id in :ids";
                return session.createQuery(hql, entityClass)
                        .setParameterList("ids", idList)
                        .list();
            }
        }
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public long count() {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            String hql = "select count(e) from " + entityClass.getName() + " e";
            Long count = session.createQuery(hql, Long.class).uniqueResult();
            return count != null ? count : 0L;
        } else {
            try (Session session = sessionFactory.openSession()) {
                String hql = "select count(e) from " + entityClass.getName() + " e";
                Long count = session.createQuery(hql, Long.class).uniqueResult();
                return count != null ? count : 0L;
            }
        }
    }

    @Override
    public void deleteById(ID id) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            T entity = session.get(entityClass, (java.io.Serializable) id);
            if (entity != null) {
                session.delete(entity);
            }
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                T entity = session.get(entityClass, (java.io.Serializable) id);
                if (entity != null) {
                    session.delete(entity);
                }
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public void delete(T entity) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            session.delete(entity);
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                session.delete(entity);
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            List<ID> idList = new ArrayList<>();
            ids.forEach(id -> idList.add((ID) id));
            if (!idList.isEmpty()) {
                String hql = "delete from " + entityClass.getName() + " e where e.id in :ids";
                session.createQuery(hql).setParameterList("ids", idList).executeUpdate();
            }
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                List<ID> idList = new ArrayList<>();
                ids.forEach(id -> idList.add((ID) id));
                if (!idList.isEmpty()) {
                    String hql = "delete from " + entityClass.getName() + " e where e.id in :ids";
                    session.createQuery(hql).setParameterList("ids", idList).executeUpdate();
                }
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            for (T entity : entities) {
                session.delete(entity);
            }
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                for (T entity : entities) {
                    session.delete(entity);
                }
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public void deleteAll() {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            String hql = "delete from " + entityClass.getName();
            session.createQuery(hql).executeUpdate();
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                String hql = "delete from " + entityClass.getName();
                session.createQuery(hql).executeUpdate();
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        }
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    @Override
    public void deleteAllInBatch(Iterable<T> entities) {
        deleteAll(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<ID> ids) {
        deleteAllById(ids);
    }

    @Override
    public void flush() {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            session.flush();
        } else {
            Transaction tx = null;
            try (Session session = sessionFactory.openSession()) {
                tx = session.beginTransaction();
                session.flush();
                tx.commit();
            } catch (Exception e) {
                if (tx != null) {
                    try {
                        tx.rollback();
                    } catch (Exception rollbackEx) {
                    }
                }
                throw new RuntimeException("Failed to flush changes to database", e);
            }
        }
    }

    @Override
    public List<T> findAll(Pageable pageable) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            return session.createQuery("from " + entityClass.getName(), entityClass)
                    .setFirstResult(pageable.getOffset())
                    .setMaxResults(pageable.getPageSize())
                    .list();
        } else {
            try (Session session = sessionFactory.openSession()) {
                return session.createQuery("from " + entityClass.getName(), entityClass)
                        .setFirstResult(pageable.getOffset())
                        .setMaxResults(pageable.getPageSize())
                        .list();
            }
        }
    }

    @Override
    public List<T> findAll(Sort sort) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            String hql = "from " + entityClass.getName() + " e order by e." + sort.getProperty() + " " + sort.getDirection();
            return session.createQuery(hql, entityClass).list();
        } else {
            try (Session session = sessionFactory.openSession()) {
                String hql = "from " + entityClass.getName() + " e order by e." + sort.getProperty() + " " + sort.getDirection();
                return session.createQuery(hql, entityClass).list();
            }
        }
    }

    @Override
    public List<T> findAll(Pageable pageable, Sort sort) {
        TransactionContext.TransactionInfo currentTx = TransactionContext.getCurrentTransaction();

        if (currentTx != null) {
            Session session = currentTx.getSession();
            String hql = "from " + entityClass.getName() + " e order by e." + sort.getProperty() + " " + sort.getDirection();
            return session.createQuery(hql, entityClass)
                    .setFirstResult(pageable.getOffset())
                    .setMaxResults(pageable.getPageSize())
                    .list();
        } else {
            try (Session session = sessionFactory.openSession()) {
                String hql = "from " + entityClass.getName() + " e order by e." + sort.getProperty() + " " + sort.getDirection();
                return session.createQuery(hql, entityClass)
                        .setFirstResult(pageable.getOffset())
                        .setMaxResults(pageable.getPageSize())
                        .list();
            }
        }
    }
}
