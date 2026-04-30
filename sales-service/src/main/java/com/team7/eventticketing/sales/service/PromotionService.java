package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.dto.PromotionDTO;
import com.team7.eventticketing.sales.dto.PromotionUsageDTO;
import com.team7.eventticketing.sales.model.Promotion;
import com.team7.eventticketing.sales.repository.PromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PromotionService {

    @Autowired
    private PromotionRepository promotionRepository;

    public PromotionDTO save(PromotionDTO promotionDTO) {
        Promotion promotion = convertToEntity(promotionDTO);
        return convertToDTO(promotionRepository.save(promotion));
    }

    public Optional<PromotionDTO> findById(Long id) {
        return promotionRepository.findById(id).map(this::convertToDTO);
    }

    public List<PromotionDTO> findAll() {
        return promotionRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public void deleteById(Long id) {
        promotionRepository.deleteById(id);
    }

    public PromotionDTO convertToDTO(Promotion promotion) {
        PromotionDTO dto = new PromotionDTO();
        dto.setId(promotion.getId());
        dto.setCode(promotion.getCode());
        dto.setDiscountType(promotion.getDiscountType());
        dto.setDiscountValue(promotion.getDiscountValue());
        dto.setMaxUses(promotion.getMaxUses());
        dto.setCurrentUses(promotion.getCurrentUses());
        dto.setExpiryDate(promotion.getExpiryDate());
        dto.setActive(promotion.getActive());
        dto.setMetadata(promotion.getMetadata());
        return dto;
    }

    public Promotion convertToEntity(PromotionDTO dto) {
        Promotion promotion = new Promotion();
        promotion.setId(dto.getId());
        promotion.setCode(dto.getCode());
        promotion.setDiscountType(dto.getDiscountType());
        promotion.setDiscountValue(dto.getDiscountValue());
        promotion.setMaxUses(dto.getMaxUses());
        if (dto.getCurrentUses() != null) promotion.setCurrentUses(dto.getCurrentUses());
        promotion.setExpiryDate(dto.getExpiryDate());
        if (dto.getActive() != null) promotion.setActive(dto.getActive());
        promotion.setMetadata(dto.getMetadata());
        return promotion;
    }

    @Cacheable(value = "top-used-promotions", key = "#limit")
    public List<PromotionUsageDTO> getTopUsedPromotions(int limit) {
        List<Object[]> results = promotionRepository.getTopUsedPromotions(limit);

        List<PromotionUsageDTO> dtoList = new ArrayList<>();

        for (Object[] row : results) {

            Long id = ((Number) row[0]).longValue();
            String code = row[1] != null ? row[1].toString() : null;
            String discountType = row[2] != null ? row[2].toString() : null;
            Double discountValue = ((Number) row[3]).doubleValue();
            Integer timesUsed = ((Number) row[4]).intValue();
            Double totalDiscountGiven = ((Number) row[5]).doubleValue();
            Boolean active = (Boolean) row[6];

            LocalDateTime expiryDate = null;
            if (row[7] != null) {
                if (row[7] instanceof java.sql.Timestamp ts) {
                    expiryDate = ts.toLocalDateTime();
                } else if (row[7] instanceof LocalDateTime ldt) {
                    expiryDate = ldt;
                }
            }
            Boolean expired = expiryDate != null && expiryDate.isBefore(LocalDateTime.now());
            PromotionUsageDTO dto = new PromotionUsageDTO.Builder()
                    .promotionId(id)
                    .code(code)
                    .discountType(discountType)
                    .discountValue(discountValue)
                    .timesUsed(timesUsed)
                    .totalDiscountGiven(totalDiscountGiven)
                    .active(active)
                    .expired(expired)
                    .build();
            dtoList.add(dto);
        }

        return dtoList;
    }
}
