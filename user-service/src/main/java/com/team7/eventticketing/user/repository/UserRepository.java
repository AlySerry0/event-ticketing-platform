package com.team7.eventticketing.user.repository;

import com.team7.eventticketing.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.team7.eventticketing.user.model.UserRole;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    // --- S1-F1: Search Users (Clean JPQL with Enum Casting) ---
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%')) AND " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')) AND " +
            "(cast(:role as text) IS NULL OR u.role = :role)")
    List<User> searchUsers(@Param("name") String name,
                           @Param("email") String email,
                           @Param("role") UserRole role);
}