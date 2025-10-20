package com.example.majorproject.Controllers;

import com.example.majorproject.Dtos.CreateUserDto;
import com.example.majorproject.Dtos.LoginRequestDto;
import com.example.majorproject.Models.User;
import com.example.majorproject.Security.JwtService;
import com.example.majorproject.Services.CustomUserDetailsService;
import com.example.majorproject.Services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @Operation(summary = "Register a new user", description = "Creates a new user and also triggers wallet creation + greeting notification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/register")
    public User registerUser(@Valid @RequestBody CreateUserDto createUserDto) {
        return userService.create(createUserDto);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequestDto loginDto){

        // 1️⃣ authenticate with AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword())
        );

        // 2️⃣ load user details
        User user = userService.findByEmail(loginDto.getEmail());
        UserDetails userDetails = userDetailsService.loadUserByUsername(loginDto.getEmail());

        // 3️⃣ generate token
        String token = jwtService.generateToken(userDetails,user.getId());

        // 4️⃣ return token as JSON
        Map<String, String> response = new HashMap<>();
        response.put("token", token);

        return ResponseEntity.ok(response);
    }



}