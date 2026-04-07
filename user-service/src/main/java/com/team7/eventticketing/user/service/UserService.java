package com.team7.eventticketing.user.service;

import com.team7.eventticketing.user.dto.*;
import com.team7.eventticketing.user.model.*;
import com.team7.eventticketing.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team7.eventticketing.user.dto.TopAttendeeDTO;
import java.time.LocalDateTime;

import com.team7.eventticketing.user.model.UserRole;
import com.team7.eventticketing.user.model.User;

import java.util.HashMap;
import java.util.Map;

import java.util.List;
import java.util.stream.Collectors;

import com.team7.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team7.eventticketing.user.repository.BookingSummaryProjection;

import java.util.Arrays;

@Service
@Transactional(readOnly = true)
public class UserService {


    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Create a new user
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
                createUserDTO.getRole()
        );

        // Added this line because prefrences was not saved befor
        if (createUserDTO.getPreferences() != null) {
            user.setPreferences(createUserDTO.getPreferences());
        }

        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    /**
     * Get user by ID
     */
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found with ID: " + id
                ));
        return convertToDTO(user);
    }

    /**
     * Get user by email
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
     * Get user by phone
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
     * Get all users
     */
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Update user
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
        return convertToDTO(updatedUser);
    }

    /**
     * Deactivate user
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
        return convertToDTO(updatedUser);
    }

    /**
     * Activate user
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
        return convertToDTO(updatedUser);
    }

    /**
     * Delete user
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
    }

    /**
     * [S1-F1] Search Users with Filters
     */
    public List<UserDTO> searchUsers(String name, String email, String role) {

        // 1. The Teammate's Trick: Use empty strings to prevent the PostgreSQL bytea crash
        String searchName = (name != null) ? name : "";
        String searchEmail = (email != null) ? email : "";

        // 2. The List Trick: Prevent the PostgreSQL Enum crash
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
     * [S1-F2] Update User Preferences (JSONB)
     */
    @Transactional
    public UserDTO updateUserPreferences(Long id, Map<String, Object> newPreferences) {
        // 1. Find the user or throw a 404 Not Found
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        // 2. Get current preferences, or initialize a new map if it's null
        Map<String, Object> currentPreferences = user.getPreferences();
        if (currentPreferences == null) {
            currentPreferences = new HashMap<>();
        }

        // 3. Merge the new preferences into the existing ones
        if (newPreferences != null) {
            currentPreferences.putAll(newPreferences);
        }

        // 4. Save and return
        user.setPreferences(currentPreferences);
        User updatedUser = userRepository.save(user);

        return convertToDTO(updatedUser);
    }

    /**
     * [S1-F3] Get User Booking Summary
     */
    public UserBookingSummaryDTO getBookingSummary(Long id) {
        // 1. Find user or throw 404 (Test Scenario D)
        userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        // 2. Execute Native SQL JOIN query
        BookingSummaryProjection projection = userRepository.getUserBookingSummary(id);

        // 3. Build and return DTO
        UserBookingSummaryDTO dto = new UserBookingSummaryDTO();
        dto.setUserId(projection.getUserId());
        dto.setName(projection.getName());
        dto.setTotalBookings(projection.getTotalBookings());
        dto.setCompletedBookings(projection.getCompletedBookings());
        dto.setCancelledBookings(projection.getCancelledBookings());
        dto.setTotalSpent(projection.getTotalSpent());
        dto.setAverageBookingAmount(projection.getAverageBookingAmount());

        return dto;
    }

    /**
     * [S1-F5] Filter Users by Preference (JSONB Query)
     */
    public List<UserDTO> filterUsersByPreference(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Key must not be blank");
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Value must not be blank");
        }

        return userRepository.findByPreferenceKeyValue(key, value)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * [S1-F6] Top Attendees by Spending (Report DTO)
     */
    public List<TopAttendeeDTO> getTopAttendeesBySpending(
                LocalDateTime startDate, LocalDateTime endDate,int limit){

            if (startDate.isAfter(endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Start date must not be after end date");
            }

            List<Object[]> results = userRepository
                    .findTopAttendeesBySpending(startDate, endDate, limit);

            return results.stream().map(row -> new TopAttendeeDTO(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    ((Number) row[2]).doubleValue(),
                    ((Number) row[3]).longValue()
            )).collect(Collectors.toList());
        }

        /**
         * [S1-F8] Get User Profile with Favorite Venues
         */
        public UserProfileDTO getUserProfileWithFavoriteVenues (Long id){
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "User not found with ID: " + id
                    ));

            UserProfileDTO userProfileDTO = new UserProfileDTO();
            userProfileDTO.setUserId(user.getId());
            userProfileDTO.setName(user.getName());
            userProfileDTO.setEmail(user.getEmail());
            userProfileDTO.setPhone(user.getPhone());
            userProfileDTO.setPreferences(user.getPreferences());
            userProfileDTO.setFavoriteVenues(user.getFavoriteVenues()
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList()));
            userProfileDTO.setTotalFavoriteVenues(user.getFavoriteVenues().size());

            return userProfileDTO;

        }

        /**
         * [S1-F9] Find Users by Favorite Category with Minimum Bookings
         */
        public List<UserDTO> findUsersByFavoriteCategoryWithMinBookings(String category, int minBookings) {
            if (category == null || category.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category must not be blank");
            }

            return userRepository.findUsersByFavoriteCategoryWithMinBookings(category, minBookings)
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }

        /**
         * Convert User entity to UserDTO
         */
        private UserDTO convertToDTO (User user){
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setName(user.getName());
            userDTO.setEmail(user.getEmail());
            userDTO.setPhone(user.getPhone());
            userDTO.setRole(user.getRole());
            userDTO.setStatus(user.getStatus());
            userDTO.setPreferences(user.getPreferences());
            userDTO.setCreatedAt(user.getCreatedAt());
            return userDTO;
        }

        private FavoriteVenueDTO convertToDTO (FavoriteVenue favoriteVenue){
            FavoriteVenueDTO dto = new FavoriteVenueDTO();
            dto.setId(favoriteVenue.getId());
            dto.setLabel(favoriteVenue.getLabel());
            dto.setVenueName(favoriteVenue.getVenueName());
            dto.setLocation(favoriteVenue.getLocation());
            dto.setCapacity(favoriteVenue.getCapacity());
            dto.setIsDefault(favoriteVenue.getIsDefault());
            dto.setMetadata(favoriteVenue.getMetadata());
            return dto;
        }
    }