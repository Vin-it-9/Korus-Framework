package com.korus.framework.test;

import com.korus.framework.annotations.Repository;
import com.korus.framework.data.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Integer> {

    User findByName(String name);

    User findByEmail(String email);

    Optional<User> findById(Integer id);

    void deleteById(Integer id);

    void deleteByEmail(String email);

    void deleteByName(String name);

    List<User> findAll();


}
