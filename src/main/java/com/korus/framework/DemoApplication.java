package com.korus.framework;

import com.korus.framework.annotations.Application;
import com.korus.framework.annotations.Transactional;
import com.korus.framework.context.ApplicationContext;

import com.korus.framework.service.UserService;

import java.util.List;

@Application
public class DemoApplication {

    public static void main(String[] args) throws InterruptedException {

        ApplicationContext context = korus.run(DemoApplication.class, args);

        UserRepository userRepository = context.getBean(UserRepository.class);
        UserService userService = context.getBean(UserService.class);

        System.out.println("\n=== Testing @Transactional Framework ===");

        User user1 = new User();
        user1.setName("Alice");
        user1.setEmail("alice@test.com");
        userRepository.save(user1);

        User user2 = new User();
        user2.setName("Bob");
        user2.setEmail("bob@test.com");
        userRepository.save(user2);

        System.out.println("\n1. Testing successful transaction...");
        try {
            userService.transferUserData(user1.getId(), user2.getId());
        } catch (Exception e) {
            System.out.println("❌ Transfer failed: " + e.getMessage());
        }

        System.out.println("\n1.1 Rollback test...");
        try {
            userService.testTransactionRollback();
        }
        catch (Exception e) {
            System.out.println("❌ Rollback test failed (expected): " + e.getMessage());
        }

        System.out.println("\n2. Testing transaction rollback...");
        try {
            userService.createUserWithValidation("TestUser", "invalid@email.com");
        } catch (Exception e) {
            System.out.println("❌ User creation failed (expected): " + e.getMessage());
        }

        System.out.println("\n3. Testing read-only transaction...");
        List<User> allUsers = userService.getAllUsersReadOnly();
        System.out.println("✅ Found " + allUsers.size() + " users in read-only transaction");

        System.out.println("\n4. Testing REQUIRES_NEW propagation...");
        userService.logUserActivity(user1.getId(), "Login");

        System.out.println("\n=== @Transactional Framework Testing Complete! ===");

        Thread.currentThread().join();
    }

}
