package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query(value = """
    SELECT 
        p.id,
        p.code,
        p.discount_type,
        p.discount_value,
        COUNT(sp.id) AS times_used,
        COALESCE(SUM(sp.discount_applied), 0) AS total_discount_given,
        p.active,
        p.expiry_date
    FROM promotions p
    LEFT JOIN sale_promotions sp
        ON p.id = sp.promotion_id
    GROUP BY p.id, p.code, p.discount_type, p.discount_value, p.active, p.expiry_date
    ORDER BY times_used DESC, p.id DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> getTopUsedPromotions(@Param("limit") int limit);
}


