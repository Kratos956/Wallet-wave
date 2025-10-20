package com.example.majorproject.Dtos;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class UpdateUserDto {

    @Size(min = 2, message = "Name must have at least 2 characters")
    private String name;

    @Min(value = 18, message = "Age must be at least 18")
    private Integer age;

    @Email(message = "Email should be valid")
    private String email;

    @Size(min = 10, max = 15, message = "Phone must be 10–15 digits")
    private String phone;
}
