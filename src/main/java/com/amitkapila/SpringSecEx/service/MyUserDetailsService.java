package com.amitkapila.SpringSecEx.service;

import com.amitkapila.SpringSecEx.model.UserPrincipal;
import com.amitkapila.SpringSecEx.model.Users;
import com.amitkapila.SpringSecEx.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo userRepo;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userRepo.findByUsername(username);

        if(user == null) {
            System.out.println("User not found: " + username);
            throw new UsernameNotFoundException("User not found: " + username);
        }else {
            System.out.println("User found: " + username);
            return new UserPrincipal(user);
        }



    }
}
