package com.team7.eventticketing.sales.controller;

import com.team7.eventticketing.sales.model.Promotion;
import com.team7.eventticketing.sales.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    @Autowired
    private PromotionService promotionService;

    @PostMapping
    public Promotion create(@RequestBody Promotion promotion) {
        return promotionService.save(promotion);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Promotion> getById(@PathVariable Long id) {
        return promotionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Promotion> getAll() {
        return promotionService.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Promotion> update(@PathVariable Long id, @RequestBody Promotion promotionDetails) {
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (promotionService.findById(id).isPresent()) {
            promotionService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
