package com.amitkapila.SpringSecEx.controller;

import com.amitkapila.SpringSecEx.model.Users;
import com.amitkapila.SpringSecEx.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Users register(@RequestBody Users user) {
        // Logic to save the user to the database
        return userService.register(user);
    }

    @PostMapping("/login")
    public String login(@RequestBody Users user) {
        return userService.verify(user);
//        System.out.println(user.getUsername() + " " + user.getPassword());
//        return "Login successful";
    }
}
