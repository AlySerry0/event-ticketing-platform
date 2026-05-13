package com.team7.eventticketing.user.service;

import com.team7.eventticketing.user.dto.*;
import com.team7.eventticketing.user.model.*;
import com.team7.eventticketing.user.observer.EntityObserver;
import com.team7.eventticketing.user.observer.MongoEventLogger;
import com.team7.eventticketing.user.repository.AuthEventRepository;
import com.team7.eventticketing.user.repository.UserRepository;
import com.team7.eventticketing.user.util.CacheInvalidationService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team7.eventticketing.user.dto.TopAttendeeDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.eventticketing.user.model.UserRole;
import com.team7.eventticketing.user.model.User;

import java.time.LocalTime;
import java.util.*;

import java.util.stream.Collectors;

import com.team7.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team7.eventticketing.user.repository.BookingSummaryProjection;

import com.team7.eventticketing.user.adapter.ObjectArrayDtoAdapter;
import com.team7.eventticketing.contracts.events.UserDeactivatedEvent;
import com.team7.eventticketing.user.messaging.publishers.UserEventPublisher;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final CacheInvalidationService cacheInvalidationService;
    private final UserEventPublisher userEventPublisher;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();

    private final List<EntityObserver> observers = new ArrayList<>();

    public UserService(UserRepository userRepository,
                       CacheInvalidationService cacheInvalidationService,
                       AuthEventRepository authEventRepository,
                       UserEventPublisher userEventPublisher) {
        this.userRepository = userRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.userEventPublisher = userEventPublisher;
//        this.registerObserver(new MongoEventLogger(authEventRepository));
        this.registerObserver(new MongoEventLogger(authEventRepository, cacheInvalidationService));
    }

    // -----------------------------------------------------------------------
    // Observer management methods (Section 3.3)
    // -----------------------------------------------------------------------

    public void registerObserver(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(EntityObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notifies all registered observers of a state change.
     * The first argument is the action string (what happened).
     * The second argument is the payload (relevant data).
     */
    private void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }

    /**
     * Create a new user
     * Per PDF §4.4.4: POST creates do NOT invalidate caches.
     */
    @Transactional
    public UserDTO createUser(CreateUserDTO createUserDTO) {
        if (userRepository.existsByEmail(createUserDTO.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email already exists: " + createUserDTO.getEmail()
            );
        }

        if (userRepository.existsByPhone(createUserDTO.getPhone())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phone already exists: " + createUserDTO.getPhone()
            );
        }

        User user = new User(
                createUserDTO.getName(),
                createUserDTO.getEmail(),
                createUserDTO.getPassword(),
                createUserDTO.getPhone(),
                UserRole.ATTENDEE
        );

        if (createUserDTO.getPreferences() != null) {
            user.setPreferences(createUserDTO.getPreferences());
        }

        User savedUser = userRepository.save(user);
        notifyObservers("USER_CREATED", Map.of(
                "userId", savedUser.getId(),
                "email", savedUser.getEmail(),
                "role", savedUser.getRole(),
                "timestamp", savedUser.getCreatedAt()
        ));
        return convertToDTO(savedUser);
    }

    /**
     * Get user by ID — CRUD detail cache (15 min)
     */
    @Cacheable(value = "user", key = "#id")
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id
                ));
        return convertToDTO(user);
    }

    /**
     * Get user by email (NOT cached per PDF §4.4.2 — only get-by-ID caches)
     */
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with email: " + email
                ));
        return convertToDTO(user);
    }

    /**
     * Get user by phone (NOT cached per PDF §4.4.2)
     */
    public UserDTO getUserByPhone(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with phone: " + phone
                ));
        return convertToDTO(user);
    }

    /**
     * Get all users (NOT cached — list endpoint per PDF §4.4.2)
     */
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Update user — invalidate user detail + all feature caches
     */
    @Transactional
    public UserDTO updateUser(Long id, UpdateUserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id
                ));

        if (userDTO.getName() != null) {
            user.setName(userDTO.getName());
        }

        if (userDTO.getEmail() != null && !userDTO.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(userDTO.getEmail())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Email already exists: " + userDTO.getEmail()
                );
            }
            user.setEmail(userDTO.getEmail());
        }

        if (userDTO.getPassword() != null) {
            user.setPassword(userDTO.getPassword());
        }

        if (userDTO.getPhone() != null && !userDTO.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(userDTO.getPhone())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phone already exists: " + userDTO.getPhone()
                );
            }
            user.setPhone(userDTO.getPhone());
        }

        User updatedUser = userRepository.save(user);
        invalidateUserCaches(id);
        notifyObservers("USER_UPDATED", Map.of(
                "userId", updatedUser.getId(),
                "email", updatedUser.getEmail(),
                "role", updatedUser.getRole(),
                "timestamp", LocalDateTime.now()
        ));
        return convertToDTO(updatedUser);
    }

    /**
     * Deactivate user — invalidate detail + features
     */
    @Transactional
    public UserDTO deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id
                ));

        boolean hasActiveBookings = userRepository.existsActiveBookingForUser(id);

        if (hasActiveBookings) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User has active bookings"
            );
        }

        user.setStatus(UserStatus.DEACTIVATED);
        User updatedUser = userRepository.save(user);
        invalidateUserCaches(id);
        notifyObservers("USER_DEACTIVATED", Map.of(
                "userId", updatedUser.getId(),
                "email", updatedUser.getEmail(),
                "role", updatedUser.getRole(),
                "timestamp", LocalDateTime.now()
        ));

        // M3 S1-EVENTS: publish user.deactivated to user.events exchange
        userEventPublisher.publishUserDeactivated(new UserDeactivatedEvent(updatedUser.getId()));

        return convertToDTO(updatedUser);
    }

    /**
     * Activate user — invalidate detail + features
     */
    @Transactional
    public UserDTO activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id
                ));

        user.setStatus(UserStatus.ACTIVE);
        User updatedUser = userRepository.save(user);
        invalidateUserCaches(id);
        notifyObservers("USER_ACTIVATED", Map.of(
                "userId", updatedUser.getId(),
                "email", updatedUser.getEmail(),
                "role", updatedUser.getRole(),
                "timestamp", LocalDateTime.now()
        ));
        return convertToDTO(updatedUser);
    }

    /**
     * Delete user — invalidate detail + features
     */
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found with ID: " + id
            );
        }
        userRepository.deleteById(id);
        invalidateUserCaches(id);
        notifyObservers("USER_DELETED", Map.of(
                "userId", id,
                "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * [S1-F1] Search Users with Filters — cached (5 min)
     */
    @Cacheable(value = "S1-F1")
    public List<UserDTO> searchUsers(String name, String email, String role) {
        String searchName = (name != null) ? name : "";
        String searchEmail = (email != null) ? email : "";

        List<UserRole> rolesToSearch;
        if (role == null || role.isBlank()) {
            rolesToSearch = Arrays.asList(UserRole.values());
        } else {
            rolesToSearch = List.of(UserRole.valueOf(role.toUpperCase()));
        }

        return userRepository.searchUsers(searchName, searchEmail, rolesToSearch)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * [S1-F2] Update User Preferences (JSONB) — invalidate detail + features
     */
    @Transactional
    public UserDTO updateUserPreferences(Long id, Map<String, Object> newPreferences) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        Map<String, Object> currentPreferences = user.getPreferences();
        if (currentPreferences == null) {
            currentPreferences = new HashMap<>();
        }

        if (newPreferences != null) {
            currentPreferences.putAll(newPreferences);
        }

        notifyObservers("USER_UPDATED", Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "updatedFields", List.of("preferences"),
                "timestamp", LocalDateTime.now()
        ));
        user.setPreferences(currentPreferences);
        User updatedUser = userRepository.save(user);
        invalidateUserCaches(id);

        return convertToDTO(updatedUser);
    }

    /**
     * [S1-F3] Get User Booking Summary — cached (10 min)
     */
//    @Cacheable(value = "S1-F3", key = "#id")
//    public UserBookingSummaryDTO getBookingSummary(Long id) {
//        userRepository.findById(id)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
//
//        BookingSummaryProjection projection = userRepository.getUserBookingSummary(id);
//
//        UserBookingSummaryDTO dto = new UserBookingSummaryDTO();
//        dto.setUserId(projection.getUserId());
//        dto.setName(projection.getName());
//        dto.setTotalBookings(projection.getTotalBookings());
//        dto.setCompletedBookings(projection.getCompletedBookings());
//        dto.setCancelledBookings(projection.getCancelledBookings());
//        dto.setTotalSpent(projection.getTotalSpent());
//        dto.setAverageBookingAmount(projection.getAverageBookingAmount());
//
//        return dto;
//    }
    /**
     * [S1-F3] Get User Booking Summary
     * Native SQL Object[] row → UserBookingSummaryDTO via ObjectArrayDtoAdapter (PDF §3.8).
     */
    @Cacheable(value = "S1-F3", key = "#id")
    public UserBookingSummaryDTO getBookingSummary(Long id) {
        userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        List<Object[]> rows = userRepository.getUserBookingSummary(id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No booking summary found for user ID: " + id);
        }
        return objectArrayDtoAdapter.toUserBookingSummaryDTO(rows.get(0));
    }


    /**
     * [S1-F5] Filter Users by Preference (JSONB Query) — cached (5 min)
     */
    @Cacheable(value = "S1-F5")
    public List<UserDTO> filterUsersByPreference(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key must not be blank");
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value must not be blank");
        }

        return userRepository.findByPreferenceKeyValue(key, value)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * [S1-F6] Top Attendees by Spending — cached (10 min)
     */
    @Cacheable(value = "S1-F6")
    public List<TopAttendeeDTO> getTopAttendeesBySpending(
            LocalDate startDate, LocalDate endDate, int limit) {

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start date must not be after end date");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

//        List<Object[]> results = userRepository
//                .findTopAttendeesBySpending(startDateTime, endDateTime, limit);
//
//        return results.stream().map(row -> new TopAttendeeDTO(
//                ((Number) row[0]).longValue(),
//                (String) row[1],
//                ((Number) row[2]).doubleValue(),
//                ((Number) row[3]).longValue()
//        )).collect(Collectors.toList());
        List<Object[]> results = userRepository
                .findTopAttendeesBySpending(startDateTime, endDateTime, limit);

        return results.stream()
                .map(objectArrayDtoAdapter::toTopAttendeeDTO)
                .collect(Collectors.toList());
    }

    /**
     * [S1-F8] Get User Profile with Favorite Venues — cached (15 min)
     */
    @Cacheable(value = "S1-F8", key = "#id")
    public UserProfileDTO getUserProfileWithFavoriteVenues(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id
                ));

        return UserProfileDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .preferences(user.getPreferences())
                .favoriteVenues(user.getFavoriteVenues()
                        .stream()
                        .map(this::convertToDTO)
                        .collect(Collectors.toList()))
                .totalFavoriteVenues(user.getFavoriteVenues().size())
                .build();
    }

    /**
     * [S1-F9] Find Users by Favorite Category with Minimum Bookings — cached (10 min)
     */
    @Cacheable(value = "S1-F9")
    public List<UserDTO> findUsersByFavoriteCategoryWithMinBookings(String category, Integer minBookings) {
        if (category == null || category.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category must not be blank");
        }

        return userRepository.findUsersByFavoriteCategoryWithMinBookings(category, minBookings)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * [CC-2] Change role — invalidate detail + S1-F12 (activity feed) per PDF §4.4.4
     */
    @Transactional
    public UserDTO changeRole(Long id, String roleStr) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        UserRole newRole;
        try {
            newRole = UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid role: " + roleStr);
        }
        String oldRole = user.getRole().name();
        user.setRole(newRole);
        user = userRepository.save(user);

        notifyObservers("ROLE_CHANGED", Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "oldRole", oldRole,
                "newRole", newRole.name(),
                "timestamp", LocalDateTime.now()
        ));
        // Invalidate user detail + activity feed (S1-F12) per PDF §4.4.4 M2 rule
        cacheInvalidationService.invalidateCacheWildcard("user-service::user::" + id);
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F12::*");
        // Also invalidate generic user feature caches that show role
        invalidateFeatureCaches();

        return convertToDTO(user);
    }

    /**
     * Wildcard-invalidate the user detail cache for {id} and all S1-F* feature caches.
     * Per PDF §4.4.6: over-invalidation is acceptable; correctness beats hit ratio.
     */
    private void invalidateUserCaches(Long id) {
        cacheInvalidationService.invalidateCacheWildcard("user-service::user::" + id);
        invalidateFeatureCaches();
    }

    private void invalidateFeatureCaches() {
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F1::*");
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F3::*");
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F8::*");
        cacheInvalidationService.invalidateCacheWildcard("user-service::S1-F9::*");
    }

    /**
     * Convert User entity to UserDTO
     */
    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .preferences(user.getPreferences())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private FavoriteVenueDTO convertToDTO(FavoriteVenue favoriteVenue) {
        return FavoriteVenueDTO.builder()
                .id(favoriteVenue.getId())
                .label(favoriteVenue.getLabel())
                .venueName(favoriteVenue.getVenueName())
                .location(favoriteVenue.getLocation())
                .capacity(favoriteVenue.getCapacity())
                .isDefault(favoriteVenue.getIsDefault())
                .metadata(favoriteVenue.getMetadata())
                .build();
    }
}