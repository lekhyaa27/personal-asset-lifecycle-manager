package com.assetmanager.model;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterForm {
    @NotBlank(message = "Full name is required") @Size(max = 100) private String fullName;
    @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") private String email;
    @NotBlank(message = "Password is required")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "Password must be at least 8 characters with a letter and a number")
    private String password;
    @NotBlank(message = "Please confirm your password") private String confirmPassword;
}
