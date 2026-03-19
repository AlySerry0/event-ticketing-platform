package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.model.SalePromotion;
import com.team7.eventticketing.sales.repository.SalePromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SalePromotionService {

    @Autowired
    private SalePromotionRepository salePromotionRepository;

    public SalePromotion save(SalePromotion salePromotion) {
        return salePromotionRepository.save(salePromotion);
    }

    public Optional<SalePromotion> findById(Long id) {
        return salePromotionRepository.findById(id);
    }

    public List<SalePromotion> findAll() {
        return salePromotionRepository.findAll();
    }

    public void deleteById(Long id) {
        salePromotionRepository.deleteById(id);
    }
}
