package com.team7.eventticketing.user.repository;

import com.team7.eventticketing.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.team7.eventticketing.user.model.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.team7.eventticketing.user.repository.BookingSummaryProjection;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    @Query("SELECT u FROM User u WHERE " +
            "(:name = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
            "u.role IN :roles")
    List<User> searchUsers(@Param("name") String name,
                           @Param("email") String email,
                           @Param("roles") List<UserRole> roles);

//    @Query(value = "SELECT " +
//            "u.id AS userId, " +
//            "u.name AS name, " +
//            "COUNT(b.id) AS totalBookings, " +
//            "COUNT(CASE WHEN b.status::text = 'COMPLETED' THEN 1 END) AS completedBookings, " +
//            "COUNT(CASE WHEN b.status::text = 'CANCELLED' THEN 1 END) AS cancelledBookings, " +
//            "COALESCE(SUM(CASE WHEN b.status::text = 'COMPLETED' THEN b.total_amount ELSE 0 END), 0) AS totalSpent, " +
//            "COALESCE(AVG(CASE WHEN b.status::text = 'COMPLETED' THEN b.total_amount ELSE NULL END), 0) AS averageBookingAmount " +
//            "FROM users u " +
//            "LEFT JOIN bookings b ON u.id = b.user_id " +
//            "WHERE u.id = :id " +
//            "GROUP BY u.id, u.name", nativeQuery = true)
//    BookingSummaryProjection getUserBookingSummary(@Param("id") Long id);

    @Query(value = "SELECT " +
            "u.id AS userId, " +
            "u.name AS name, " +
            "COUNT(b.id) AS totalBookings, " +
            "COUNT(CASE WHEN b.status::text = 'COMPLETED' THEN 1 END) AS completedBookings, " +
            "COUNT(CASE WHEN b.status::text = 'CANCELLED' THEN 1 END) AS cancelledBookings, " +
            "COALESCE(SUM(CASE WHEN b.status::text = 'COMPLETED' THEN b.total_amount ELSE 0 END), 0) AS totalSpent, " +
            "COALESCE(AVG(CASE WHEN b.status::text = 'COMPLETED' THEN b.total_amount ELSE NULL END), 0) AS averageBookingAmount " +
            "FROM users u " +
            "LEFT JOIN bookings b ON u.id = b.user_id " +
            "WHERE u.id = :id " +
            "GROUP BY u.id, u.name", nativeQuery = true)
    List<Object[]> getUserBookingSummary(@Param("id") Long id);

    // Check if user has any active bookings
    @Query(value = "SELECT COUNT(*) > 0 FROM bookings " +
            "WHERE user_id = :userId " +
            "AND status IN ('PENDING','CONFIRMED','CHECKED_IN')",
            nativeQuery = true)
    boolean existsActiveBookingForUser(@Param("userId") Long userId);

    //finds if a user has a specific preference
    @Query(value = "SELECT * FROM users WHERE LOWER(preferences ->> :key) = LOWER(:value)",
            nativeQuery = true)
    List<User> findByPreferenceKeyValue(@Param("key") String key,
                                        @Param("value") String value);
    @Query(value = """
        SELECT u.id AS userId, u.name AS name,
               COALESCE(SUM(b.total_amount), 0) AS totalSpent,
               COUNT(b.id) AS bookingCount
        FROM users u
        LEFT JOIN bookings b ON b.user_id = u.id
            AND b.status = 'COMPLETED'
            AND b.booking_date BETWEEN :startDate AND :endDate
        GROUP BY u.id, u.name
        HAVING COALESCE(SUM(b.total_amount), 0) > 0
        ORDER BY totalSpent DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopAttendeesBySpending(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("limit") int limit);

    // --- S1-F9: Find Users by Favorite Category with Minimum Bookings
    @Query(value = """
        SELECT u.* 
        FROM users u
        LEFT JOIN bookings b
          ON u.id = b.user_id
         AND b.status = 'COMPLETED'
        WHERE u.preferences ->> 'favoriteCategory' = :category
        GROUP BY u.id
        HAVING COUNT(b.id) >= :minBookings
        """, nativeQuery = true)
    List<User> findUsersByFavoriteCategoryWithMinBookings(@Param("category") String category,
                                                          @Param("minBookings") int minBookings);
}