package com.example.majorproject.Controllers;


import com.example.majorproject.Dtos.CreateUserDto;
import com.example.majorproject.Dtos.UpdateUserDto;
import com.example.majorproject.Models.User;
import com.example.majorproject.Services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserService userService;


    @Operation(summary = "Update user details", description = "Updates user info like name, email, phone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/me")
    public User updateUser(Authentication authentication,
                           @Valid @RequestBody UpdateUserDto updateDto) {
        String email = authentication.getName(); // email from JWT
        User user = userService.findByEmail(email);
        return userService.updateUser(user.getId(), updateDto);
    }


    @Operation(summary = "Get user by ID", description = "Fetches details of a user by their ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User fetched successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/me")
    public User getUserById(Authentication authentication) {
        String email = authentication.getName();
        return userService.findByEmail(email);
    }



}
