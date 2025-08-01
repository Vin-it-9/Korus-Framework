package com.korus.framework;

import com.korus.framework.annotations.Application;
import com.korus.framework.context.ApplicationContext;


@Application
public class DemoApplication {

    public static void main(String[] args) throws InterruptedException {

        ApplicationContext context = korus.run(DemoApplication.class, args);



        Thread.currentThread().join();
    }

}
