package com.team7.eventticketing.sales.controller;

import com.team7.eventticketing.sales.dto.PromotionDTO;
import com.team7.eventticketing.sales.dto.PromotionUsageDTO;
import com.team7.eventticketing.sales.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales/promotions")
public class PromotionController {

    @Autowired
    private PromotionService promotionService;

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PostMapping
    public PromotionDTO create(@RequestBody PromotionDTO promotionDTO) {
        return promotionService.save(promotionDTO);
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<PromotionDTO> getById(@PathVariable Long id) {
        return promotionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping
    public List<PromotionDTO> getAll() {
        return promotionService.findAll();
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<PromotionDTO> update(@PathVariable Long id, @RequestBody PromotionDTO promotionDetails) {
        return promotionService.findById(id).map(promotion -> {
            promotion.setCode(promotionDetails.getCode());
            promotion.setDiscountType(promotionDetails.getDiscountType());
            promotion.setDiscountValue(promotionDetails.getDiscountValue());
            promotion.setMaxUses(promotionDetails.getMaxUses());
            promotion.setCurrentUses(promotionDetails.getCurrentUses());
            promotion.setExpiryDate(promotionDetails.getExpiryDate());
            promotion.setActive(promotionDetails.getActive());
            promotion.setMetadata(promotionDetails.getMetadata());
            return ResponseEntity.ok(promotionService.save(promotion));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (promotionService.findById(id).isPresent()) {
            promotionService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/top-used")
    public List<PromotionUsageDTO> getTopUsedPromotions(@RequestParam(defaultValue = "10")  int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        return promotionService.getTopUsedPromotions(limit);
    }
}
