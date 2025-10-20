package com.example.majorproject.Dtos;


import com.example.majorproject.Models.User;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserDto {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "Age is required")
    @Min(18)
    private Integer age;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    private String phone;

    @NotBlank(message = "Password is required")
    private String password; // ✅ NEW FIELD

    public User convertToUser() {
        return User.builder()
                .name(name)
                .age(age)
                .email(email)
                .phone(phone)
                .password(password) // ✅ include password
                .build();
    }
}

