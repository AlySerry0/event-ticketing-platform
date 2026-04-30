package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.dto.SalePromotionDTO;
import com.team7.eventticketing.sales.model.SalePromotion;

import java.time.LocalDateTime;

import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.repository.PromotionRepository;
import com.team7.eventticketing.sales.repository.SalePromotionRepository;
import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import com.team7.eventticketing.sales.util.CacheInvalidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;



@Service
public class SalePromotionService {

    @Autowired
    private SalePromotionRepository salePromotionRepository;

    @Autowired
    private TicketSaleRepository ticketSaleRepository;

    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    CacheInvalidationService cacheInvalidationService;

    private void invalidateAfterSalePromotionWrite(SalePromotion salePromotion) {
        Long salePromotionId = salePromotion.getId();

        Long saleId = salePromotion.getTicketSale() != null
                ? salePromotion.getTicketSale().getId()
                : null;

        Long promotionId = salePromotion.getPromotion() != null
                ? salePromotion.getPromotion().getId()
                : null;

        // CRUD get-by-ID cache
        cacheInvalidationService.invalidateCacheWildcard(
                "sales-service::SalePromotion::" + salePromotionId
        );

        if (saleId != null) {
            cacheInvalidationService.invalidateCacheWildcard("sales-service::ticket-sale::" + saleId);
            cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F8::" + saleId);
            cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F11::" + saleId);
        }

        if (promotionId != null) {
            cacheInvalidationService.invalidateCacheWildcard("sales-service::promotion::" + promotionId);
        }

        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F9::*");
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F10::*");
    }

    public SalePromotionDTO save(SalePromotionDTO salePromotionDTO) {
        SalePromotion salePromotion = convertToEntity(salePromotionDTO);

        if (salePromotion.getAppliedAt() == null) {
            salePromotion.setAppliedAt(LocalDateTime.now());
        }

        SalePromotion saved = salePromotionRepository.save(salePromotion);

        invalidateAfterSalePromotionWrite(saved);

        return convertToDTO(saved);
    }
    @Cacheable(value = "sale-promotion", key = "#id")
    public Optional<SalePromotionDTO> findById(Long id) {
        return salePromotionRepository.findById(id).map(this::convertToDTO);
    }

    public List<SalePromotionDTO> findAll() {
        return salePromotionRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public void deleteById(Long id) {

        SalePromotion salePromotion = salePromotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale promotion not found"));

        salePromotionRepository.deleteById(id);

        invalidateAfterSalePromotionWrite(salePromotion);
    }

    public SalePromotionDTO convertToDTO(SalePromotion salePromotion) {
        SalePromotionDTO dto = new SalePromotionDTO();
        dto.setId(salePromotion.getId());
        if (salePromotion.getTicketSale() != null) {
            dto.setTicketSaleId(salePromotion.getTicketSale().getId());
        }
        if (salePromotion.getPromotion() != null) {
            dto.setPromotionId(salePromotion.getPromotion().getId());
        }
        dto.setDiscountApplied(salePromotion.getDiscountApplied());
        dto.setAppliedAt(salePromotion.getAppliedAt());
        return dto;
    }

    public SalePromotion convertToEntity(SalePromotionDTO dto) {
        SalePromotion salePromotion = new SalePromotion();
        salePromotion.setId(dto.getId());
        
        if (dto.getTicketSaleId() != null) {
            ticketSaleRepository.findById(dto.getTicketSaleId())
                .ifPresent(salePromotion::setTicketSale);
        }
        
        if (dto.getPromotionId() != null) {
            promotionRepository.findById(dto.getPromotionId())
                .ifPresent(salePromotion::setPromotion);
        }

        salePromotion.setDiscountApplied(dto.getDiscountApplied());
        salePromotion.setAppliedAt(dto.getAppliedAt());
        return salePromotion;
    }
}
