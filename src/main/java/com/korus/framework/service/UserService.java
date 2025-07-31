package com.korus.framework.service;


import com.korus.framework.User;
import com.korus.framework.UserRepository;
import com.korus.framework.annotations.Component;
import com.korus.framework.annotations.Propagation;
import com.korus.framework.annotations.Service;
import com.korus.framework.annotations.Transactional;

import java.util.List;

@Service
@Component
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void transferUserData(Integer fromUserId, Integer toUserId) {
        User fromUser = userRepository.getById(fromUserId);
        User toUser = userRepository.getById(toUserId);

        fromUser.setName(fromUser.getName() + " (TRANSFERRED)");
        toUser.setName(toUser.getName() + " (RECEIVED)");

        userRepository.save(fromUser);
        userRepository.save(toUser);

        System.out.println("✅ User data transfer completed successfully");
    }

    @Transactional(rollbackFor = Exception.class)
    public void createUserWithValidation(String name, String email) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        userRepository.save(user);
        if (email.contains("invalid")) {
            throw new RuntimeException("Invalid email detected");
        }

        System.out.println("✅ User created successfully");
    }

    @Transactional(readOnly = true, timeout = 30)
    public List<User> getAllUsersReadOnly() {
        return userRepository.findAll();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserActivity(Integer userId, String activity) {
        System.out.println("Logging activity for user " + userId + ": " + activity);
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class})
    public void updateUserWithPartialFailure(Integer userId, String newName) {
        User user = userRepository.getById(userId);
        user.setName(newName);
        userRepository.save(user);
        if (newName.length() > 50) {
            throw new IllegalArgumentException("Name too long, but user was still saved");
        }
    }
}
