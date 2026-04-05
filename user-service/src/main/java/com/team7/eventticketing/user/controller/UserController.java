
package com.team7.eventticketing.user.controller;

import com.team7.eventticketing.user.dto.CreateUserDTO;
import com.team7.eventticketing.user.dto.UpdateUserDTO;
import com.team7.eventticketing.user.dto.UserDTO;
import com.team7.eventticketing.user.dto.UserProfileDTO;
import com.team7.eventticketing.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Create a new user
     * POST /api/users
     */
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserDTO createUserDTO) {
        UserDTO userDTO = userService.createUser(createUserDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(userDTO);
    }

    /**
     * Get user by ID
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        UserDTO userDTO = userService.getUserById(id);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Get user by email
     * GET /api/users/email/{email}
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<UserDTO> getUserByEmail(@PathVariable String email) {
        UserDTO userDTO = userService.getUserByEmail(email);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Get user by phone
     * GET /api/users/phone/{phone}
     */
    @GetMapping("/phone/{phone}")
    public ResponseEntity<UserDTO> getUserByPhone(@PathVariable String phone) {
        UserDTO userDTO = userService.getUserByPhone(phone);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Get all users
     * GET /api/users
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> userDTOs = userService.getAllUsers();
        return ResponseEntity.ok(userDTOs);
    }

    /**
     * Update user
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UpdateUserDTO userDTO) {
        UserDTO updatedUserDTO = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(updatedUserDTO);
    }

    /**
     * Deactivate user
     * PATCH /api/users/{id}/deactivate
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable Long id) {
        UserDTO userDTO = userService.deactivateUser(id);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Activate user
     * PATCH /api/users/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserDTO> activateUser(@PathVariable Long id) {
        UserDTO userDTO = userService.activateUser(id);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Delete user
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * [S1-F1] Search Users with Filters
     * GET /api/users/search?name={name}&email={email}&role={role}
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role) {

        List<UserDTO> users = userService.searchUsers(name, email, role);
        return ResponseEntity.ok(users);
    }

    /**
     * [S1-F2] Update User Preferences (JSONB)
     * PUT /api/users/{id}/preferences
     */
    @PutMapping("/{id}/preferences")
    public ResponseEntity<UserDTO> updateUserPreferences(
            @PathVariable Long id,
            @RequestBody Map<String, Object> preferences) {

        UserDTO updatedUser = userService.updateUserPreferences(id, preferences);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * [S1-F8] Get User Profile with Favorite Venues
     * GET /api/users/{id}/profile
     */
    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable Long id) {
        UserProfileDTO userProfile = userService.getUserProfileWithFavoriteVenues(id);
        return ResponseEntity.ok(userProfile);
    }
}
