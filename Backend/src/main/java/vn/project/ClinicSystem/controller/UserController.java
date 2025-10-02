package vn.project.ClinicSystem.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.service.UserService;
import vn.project.ClinicSystem.util.error.IdInvalidException;

@RestController
public class UserController {
    private final UserService userService;

    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users/create")
    public ResponseEntity<User> createNewUser(
            @RequestBody User postManUser) {
        String hashPassword = this.passwordEncoder.encode(postManUser.getPassword());
        postManUser.setPassword(hashPassword);
        User newUser = this.userService.handleCreateUser(postManUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUserById(
            @PathVariable("id") Long id) throws IdInvalidException {
        if (id <= 0) {
            throw new IdInvalidException("Invalid khong nho hon 1");
        }
        this.userService.handleDeleteUserById(id);
        return ResponseEntity.status(HttpStatus.OK).body("delete user by id");
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable("id") Long id) {
        User fetchUser = this.userService.handleGetUserById(id);
        return ResponseEntity.status(HttpStatus.OK).body(fetchUser);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.status(HttpStatus.OK).body(this.userService.fetchGetAllUsers());
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@PathVariable("id") Long id, @RequestBody User user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        User updatedUser = this.userService.handleUpdateUserById(id, user);
        return ResponseEntity.status(HttpStatus.OK).body(updatedUser);
    }

}
