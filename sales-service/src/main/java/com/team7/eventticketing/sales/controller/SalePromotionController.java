package com.team7.eventticketing.sales.controller;

import com.team7.eventticketing.sales.dto.SalePromotionDTO;
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
    public SalePromotionDTO create(@RequestBody SalePromotionDTO salePromotionDTO) {
        return salePromotionService.save(salePromotionDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalePromotionDTO> getById(@PathVariable Long id) {
        return salePromotionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<SalePromotionDTO> getAll() {
        return salePromotionService.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalePromotionDTO> update(@PathVariable Long id, @RequestBody SalePromotionDTO salePromotionDetails) {
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
