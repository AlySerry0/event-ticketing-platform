
package com.team7.eventticketing.user.controller;

import com.team7.eventticketing.user.dto.CreateUserDTO;
import com.team7.eventticketing.user.dto.UpdateUserDTO;
import com.team7.eventticketing.user.dto.UserDTO;
import com.team7.eventticketing.user.dto.UserProfileDTO;
import com.team7.eventticketing.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.team7.eventticketing.user.dto.TopAttendeeDTO;
import org.springframework.format.annotation.DateTimeFormat;
import com.team7.eventticketing.user.dto.ActivityFeedDTO;
import com.team7.eventticketing.user.service.ActivityFeedService;
import com.team7.eventticketing.user.service.JwtService;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.eventticketing.user.dto.UserBookingSummaryDTO;

import java.util.Map;

import java.util.List;

import com.team7.eventticketing.user.security.OwnershipChecker;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ActivityFeedService activityFeedService;
    private final JwtService jwtService;
    private final OwnershipChecker ownershipChecker;

    public UserController(UserService userService,
                          ActivityFeedService activityFeedService,
                          JwtService jwtService,
                          OwnershipChecker ownershipChecker) {
        this.userService = userService;
        this.activityFeedService = activityFeedService;
        this.jwtService = jwtService;
        this.ownershipChecker = ownershipChecker;
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
     * Get user by ID — Owner OR Admin
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id,
                                               @RequestHeader("Authorization") String authHeader) {
        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
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
     * Update user — Owner OR Admin
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id,
                                              @RequestBody UpdateUserDTO userDTO,
                                              @RequestHeader("Authorization") String authHeader) {
        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
        UserDTO updatedUserDTO = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(updatedUserDTO);
    }

    /**
     * Deactivate user — Owner OR Admin
     * PATCH /api/users/{id}/deactivate
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable Long id,
                                                  @RequestHeader("Authorization") String authHeader) {
        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
        UserDTO userDTO = userService.deactivateUser(id);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Activate user — Owner OR Admin
     * PATCH /api/users/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserDTO> activateUser(@PathVariable Long id,
                                                @RequestHeader("Authorization") String authHeader) {
        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
        UserDTO userDTO = userService.activateUser(id);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Delete user — Owner OR Admin
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @RequestHeader("Authorization") String authHeader) {
        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
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
     * [S1-F2] Update User Preferences (JSONB) — Owner OR Admin
     * PUT /api/users/{id}/preferences
     */
    @PutMapping("/{id}/preferences")
    public ResponseEntity<UserDTO> updateUserPreferences(
            @PathVariable Long id,
            @RequestBody Map<String, Object> preferences,
            @RequestHeader("Authorization") String authHeader) {

        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
        UserDTO updatedUser = userService.updateUserPreferences(id, preferences);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * [S1-F3] Booking Summary — Owner OR Admin
     */
    @GetMapping("/{id}/booking-summary")
    public ResponseEntity<UserBookingSummaryDTO> getUserBookingSummary(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
        UserBookingSummaryDTO summary = userService.getBookingSummary(id);
        return ResponseEntity.ok(summary);
    }

    /**
     * [S1-F6] Top Attendees by Spending (Report DTO)
     * GET /api/users/reports/top-attendees?startDate={date}&endDate={date}&limit={n}
     */
    @GetMapping("/reports/top-attendees")
    public ResponseEntity<List<TopAttendeeDTO>> getTopAttendees(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "5") int limit) {

        List<TopAttendeeDTO> result = userService
                .getTopAttendeesBySpending(startDate, endDate, limit);
        return ResponseEntity.ok(result);
    }

    /**
     * [S1-F5] Filter Users by Preference (JSONB Query)
     * GET /api/users/preferences/search?key={key}&value={value}
     */
    @GetMapping("/preferences/search")
    public ResponseEntity<List<UserDTO>> filterUsersByPreference(
            @RequestParam String key,
            @RequestParam String value) {
        List<UserDTO> users = userService.filterUsersByPreference(key, value);
        return ResponseEntity.ok(users);
    }

    /**
     * [S1-F8] Get User Profile with Favorite Venues — Owner OR Admin
     * GET /api/users/{id}/profile
     */
    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        ownershipChecker.requireOwnerOrAdmin(id, authHeader);
        UserProfileDTO userProfile = userService.getUserProfileWithFavoriteVenues(id);
        return ResponseEntity.ok(userProfile);
    }

    /**
     * [S1-F9] Find Users by Favorite Category with Minimum Bookings
     * GET /api/users/preferences/category?category={cat}&minBookings={n}
     */
    @GetMapping("/preferences/category")
    public ResponseEntity<List<UserDTO>> findUsersByFavoriteCategoryWithMinBookings(
            @RequestParam String category,
            @RequestParam(defaultValue = "0") Integer minBookings) {
        List<UserDTO> users = userService.findUsersByFavoriteCategoryWithMinBookings(category, minBookings);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserDTO> changeRole(@PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.changeRole(id, body.get("role")));
    }

    /**
     * [S1-F12] Get User Activity Feed
     * GET /api/users/{id}/activity?page={page}&size={size}
     *
     * Auth: Required (USER)
     * Ownership: caller must be the target user OR an ADMIN
     * Cache: S1-F12, 5 minutes TTL
     */
    @GetMapping("/{id}/activity")
    public ResponseEntity<ActivityFeedDTO> getUserActivityFeed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String authHeader) {

        // Extract the raw token from "Bearer <token>"
        String token = authHeader.substring(7);

        // Ownership check per spec Section 10.1.3:
        // caller must be the target user OR an ADMIN
        Long callerUid  = jwtService.extractUserId(token);
        String callerRole = jwtService.extractRole(token);

        boolean isOwner = callerUid != null && callerUid.equals(id);
        boolean isAdmin = "ADMIN".equals(callerRole);

        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you can only view your own activity feed");
        }

        // Clamp page/size defaults per spec
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? 10 : size; // cap to 100 handled inside service

        ActivityFeedDTO feed = activityFeedService.getActivityFeed(id, safePage, safeSize);
        return ResponseEntity.ok(feed);
    }
}
