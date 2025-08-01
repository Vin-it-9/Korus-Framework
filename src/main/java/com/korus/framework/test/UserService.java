package com.korus.framework.test;

import com.korus.framework.annotations.Autowired;
import com.korus.framework.annotations.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    public UserRepository UserRepository;


    public void adduser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getName() == null || user.getEmail() == null) {
            throw new IllegalArgumentException("User name and email cannot be null");
        }
        UserRepository.save(user);
    }

    public User getuserById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return UserRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
    }
    public User getuserByName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("User name cannot be null");
        }
        return UserRepository.findByName(name);
    }
    public User getuserByEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("User email cannot be null");
        }
        return UserRepository.findByEmail(email);
    }
    public void deleteuserById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        UserRepository.deleteById(id);
    }
    public void deleteuserByEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("User email cannot be null");
        }
        UserRepository.deleteByEmail(email);
    }

    public void deleteuserByName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("User name cannot be null");
        }
        UserRepository.deleteByName(name);
    }
    public List<User> getAllUsers() {
        List<User> userList = UserRepository.findAll();
        if (userList.isEmpty()) {
            throw new IllegalArgumentException("No User found");
        }
        return userList;
    }

    public User save(User user) {
        return UserRepository.save(user);
    }

}
