package com.team7.eventticketing.user.config;

import com.team7.eventticketing.user.model.User;
import com.team7.eventticketing.user.model.UserRole;
import com.team7.eventticketing.user.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DataSeeder {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Seeds the admin account if it does not already exist.
     * @return true if a new admin account was created, false if it already existed
     */
    public boolean seedAdminAccount() {
        String adminEmail = "admin@guc.edu.eg";
        if (userRepository.existsByEmail(adminEmail)) {
            return false;
        }
        User admin = new User(
                "Admin",
                adminEmail,
                passwordEncoder.encode("admin123"),
                "01111111111",
                UserRole.ADMIN
        );
        userRepository.save(admin);
        return true;
    }

    public boolean seedUserAccount() {
        String userEmail = "user@guc.edu.eg";
        if (userRepository.existsByEmail(userEmail)) {
            return false;
        }
        User user = new User(
                "User",
                userEmail,
                passwordEncoder.encode("user123"),
                "01234567890",
                UserRole.ATTENDEE
        );
        userRepository.save(user);
        return true;
    }
}
