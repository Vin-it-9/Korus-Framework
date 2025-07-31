package com.korus.framework;

import com.korus.framework.annotations.Param;
import com.korus.framework.annotations.Query;
import com.korus.framework.annotations.Repository;
import com.korus.framework.data.JpaRepository;
import com.korus.framework.data.Pageable;

import java.util.List;


@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("SELECT u FROM com.korus.framework.User u ORDER BY u.name ASC")
    List<User> findTop10OrderByName(Pageable pageable);

    @Query("SELECT u FROM com.korus.framework.User u WHERE u.email LIKE :pattern ORDER BY u.name")
    List<User> findByEmailPattern(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT u FROM com.korus.framework.User u WHERE u.name = :name OR u.email = :email ORDER BY u.id")
    List<User> findByNameOrEmail(@Param("name") String name, @Param("email") String email, Pageable pageable);

}