package com.example.majorproject.Services;

import com.example.majorproject.Models.User;
import com.example.majorproject.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Fetch User from DB
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Build Spring Security UserDetails
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())   // this will be stored in JWT's "sub"
                .password(user.getPassword())    // must be encoded with BCrypt when saving
                .roles("USER")                   // default role
                .build();
    }
}
