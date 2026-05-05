package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.adapter.ObjectArrayDtoAdapter;
import com.team7.eventticketing.sales.dto.PromotionDTO;
import com.team7.eventticketing.sales.dto.PromotionUsageDTO;
import com.team7.eventticketing.sales.model.Promotion;
import com.team7.eventticketing.sales.repository.PromotionRepository;
import com.team7.eventticketing.sales.util.CacheInvalidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PromotionService {

    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    private ObjectArrayDtoAdapter objectArrayDtoAdapter;
    @Autowired
    private CacheInvalidationService cacheInvalidationService;
    private void invalidateAfterPromotionWrite(Long promotionId) {
        // CRUD get-by-ID cache
        cacheInvalidationService.invalidateCacheWildcard("sales-service::promotion::" + promotionId);

        // S5-F9: Most Used Promotions Report
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F9::*");

        // S5-F8 can show applied promotion details through sale details
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F8::*");

        // S5-F10 analytics may depend on promotion/tier revenue calculations
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F10::*");
    }
    public PromotionDTO save(PromotionDTO promotionDTO) {
        Promotion promotion = convertToEntity(promotionDTO);

        Promotion saved = promotionRepository.save(promotion);

        invalidateAfterPromotionWrite(saved.getId());

        return convertToDTO(saved);
    }

    @Cacheable(value = "promotion", key = "#id")
    public Optional<PromotionDTO> findById(Long id) {
        return promotionRepository.findById(id).map(this::convertToDTO);
    }

    public List<PromotionDTO> findAll() {
        return promotionRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public void deleteById(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Promotion not found"
                ));

        promotionRepository.delete(promotion);

        invalidateAfterPromotionWrite(id);
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

    @Cacheable(value = "S5-F9", key = "#limit")
    public List<PromotionUsageDTO> getTopUsedPromotions(int limit) {
        List<Object[]> results = promotionRepository.getTopUsedPromotions(limit);
        return results.stream()
                .map(objectArrayDtoAdapter::toPromotionUsageDTO)
                .toList();
    }
}
