package com.korus.framework.test;

import com.korus.framework.annotations.Repository;
import com.korus.framework.data.JpaRepository;

import java.util.List;

@Repository
public interface emprepo extends JpaRepository<emp,Integer> {

    List<emp> findByEmpname(String empname);
    List<emp> findByEmpnameAndEmpid(String empname, Integer empid);
    List<emp> findByEmpnameOrEmpid(String empname, Integer empid);
    List<emp> findByEmpnameLike(String empname);
    List<emp> findByEmpnameNotLike(String empname);
    List<emp> findByEmpnameIn(List<String> empnames);




}
