package com.fitness.UserService.Controller;

import com.fitness.UserService.dto.RegisterRequest;
import com.fitness.UserService.dto.UserResponse;
import com.fitness.UserService.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@AllArgsConstructor
public class UserController {
    private UserService userService;
    @GetMapping("/{usersId}")
    public ResponseEntity<UserResponse> getUserProfiles(@PathVariable String usersId) {
        return ResponseEntity.ok(userService.getUserProfiles(usersId));
    }
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }
    @GetMapping("/{usersId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String usersId) {
        return ResponseEntity.ok(userService.existsByUserId(usersId));
    }
}
