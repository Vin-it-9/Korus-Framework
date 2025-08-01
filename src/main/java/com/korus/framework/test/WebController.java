package com.korus.framework.test;

import com.korus.framework.annotations.*;
import java.util.List;

@RestController
public class WebController {


    @Autowired
    public UserService service;

    @Autowired
    public UserRepository repo;

    @GetMapping("/hero")
    public List<User> getAllUsers() {
        return service.getAllUsers();
    }

    @PostMapping("/test/user")
    public List<User> getAllUse() {
        return service.getAllUsers();
    }

    @GetMapping("/test/users/{id}")
    public User getUserById(@PathVariable Integer id) {
        return repo.findById(id).orElse(null);
    }

    @GetMapping("/test/users/search/name")
    public User getUserByName(@RequestParam String name) {
        return repo.findByName(name);
    }

    @GetMapping("/test/users/search/email")
    public User getUserByEmail(@RequestParam String email) {
        return repo.findByEmail(email);
    }

    @PostMapping("/test/users")
    public User saveUser(@RequestBody User user) {
        return service.save(user);
    }


    @DeleteMapping("/test/users/{id}")
    public void deleteUserById(@PathVariable Integer id) {
        repo.deleteById(id);
    }

    @DeleteMapping("/test/users/search/name")
    public void deleteUserByName(@RequestParam String name) {
        repo.deleteByName(name);
    }

    @DeleteMapping("/test/users/search/email")
    public void deleteUserByEmail(@RequestParam String email) {
        repo.deleteByEmail(email);
    }
}
