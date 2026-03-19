package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.model.Promotion;
import com.team7.eventticketing.sales.repository.PromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PromotionService {

    @Autowired
    private PromotionRepository promotionRepository;

    public Promotion save(Promotion promotion) {
        return promotionRepository.save(promotion);
    }

    public Optional<Promotion> findById(Long id) {
        return promotionRepository.findById(id);
    }

    public List<Promotion> findAll() {
        return promotionRepository.findAll();
    }

    public void deleteById(Long id) {
        promotionRepository.deleteById(id);
    }
}
