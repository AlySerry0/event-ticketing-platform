package com.team7.eventticketing.sales.controller;

import com.team7.eventticketing.sales.model.SalePromotion;
import com.team7.eventticketing.sales.service.SalePromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sale-promotions")
public class SalePromotionController {

    @Autowired
    private SalePromotionService salePromotionService;

    @PostMapping
    public SalePromotion create(@RequestBody SalePromotion salePromotion) {
        return salePromotionService.save(salePromotion);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalePromotion> getById(@PathVariable Long id) {
        return salePromotionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<SalePromotion> getAll() {
        return salePromotionService.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalePromotion> update(@PathVariable Long id, @RequestBody SalePromotion salePromotionDetails) {
        return salePromotionService.findById(id).map(salePromotion -> {
            salePromotion.setDiscountApplied(salePromotionDetails.getDiscountApplied());
            salePromotion.setAppliedAt(salePromotionDetails.getAppliedAt());
            return ResponseEntity.ok(salePromotionService.save(salePromotion));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (salePromotionService.findById(id).isPresent()) {
            salePromotionService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
