package com.korus.framework.test;

import com.korus.framework.annotations.Autowired;
import com.korus.framework.annotations.Service;

@Service
public class empservice {

    @Autowired
    public emprepo repo;


    public void addemp(emp emp) {
        repo.save(emp);
    }

    public emp getempById(Integer id) {
        return repo.findById(id).orElse(null);
    }
    public void deleteempById(Integer id) {
        repo.deleteById(id);
    }
    public void updateemp(emp emp) {
        emp existingemp = repo.findById(emp.getId()).orElse(null);
        if (existingemp != null) {
            existingemp.setName(emp.getName());
            existingemp.setEmail(emp.getEmail());
            repo.save(existingemp);
        }
    }


}
