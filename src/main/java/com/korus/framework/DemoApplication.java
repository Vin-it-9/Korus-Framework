package com.korus.framework;

import com.korus.framework.annotations.Application;
import com.korus.framework.context.ApplicationContext;
import com.korus.framework.test.*;


@Application
public class DemoApplication {

    public static void main(String[] args) throws InterruptedException {

        ApplicationContext context = korus.run(DemoApplication.class, args);

        UserRepository UserRepository = context.getBean(UserRepository.class);

        // Save user
        User u1 = new User();
        u1.setName("Vineet");
        u1.setEmail("vineet@example.com");
        u1.setPassword("pass123");
        u1.setStatus("ACTIVE");
        UserRepository.save(u1);

        // Save another user
        User u2 = new User();
        u2.setName("John");
        u2.setEmail("john@example.com");
        u2.setPassword("john123");
        u2.setStatus("INACTIVE");
        UserRepository.save(u2);



        System.out.println("Remaining User: " + UserRepository.findAll());

        Thread.currentThread().join();
    }

}
