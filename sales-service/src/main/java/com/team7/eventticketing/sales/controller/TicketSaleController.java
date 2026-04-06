package com.team7.eventticketing.sales.controller;

import com.team7.eventticketing.sales.dto.ProcessTicketDTO;
import com.team7.eventticketing.sales.dto.TicketSaleDTO;
import com.team7.eventticketing.sales.model.PaymentMethod;
import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.service.TicketSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class TicketSaleController {

    @Autowired
    private TicketSaleService ticketSaleService;

    @PostMapping
    public TicketSaleDTO create(@RequestBody TicketSaleDTO ticketSaleDTO) {
        return ticketSaleService.save(ticketSaleDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketSaleDTO> getById(@PathVariable Long id) {
        return ticketSaleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<TicketSaleDTO> getAll() {
        return ticketSaleService.findAll();
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketSaleDTO> update(@PathVariable Long id, @RequestBody TicketSaleDTO ticketSaleDetails) {
        return ticketSaleService.findById(id).map(ticketSale -> {
            ticketSale.setBookingId(ticketSaleDetails.getBookingId());
            ticketSale.setUserId(ticketSaleDetails.getUserId());
            ticketSale.setAmount(ticketSaleDetails.getAmount());
            ticketSale.setMethod(ticketSaleDetails.getMethod());
            ticketSale.setStatus(ticketSaleDetails.getStatus());
            ticketSale.setTransactionDetails(ticketSaleDetails.getTransactionDetails());
            ticketSale.setCreatedAt(ticketSaleDetails.getCreatedAt());
            return ResponseEntity.ok(ticketSaleService.save(ticketSale));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (ticketSaleService.findById(id).isPresent()) {
            ticketSaleService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    @PostMapping("/{saleId}/promotions/{promotionId}")
    public ResponseEntity<TicketSaleDTO> applyPromotion(
            @PathVariable Long saleId,
            @PathVariable Long promotionId) {

        TicketSale updatedSale = ticketSaleService.applyPromotionToSale(saleId, promotionId);
        return ResponseEntity.ok(ticketSaleService.convertToDTO(updatedSale));
    }

    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<Void> processTicketSale(
            @PathVariable Long bookingId,
            @RequestBody ProcessTicketDTO request
    ) {
        // 1. Check method exists
        if (request.getMethod() == null || request.getMethod().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // 2. Convert to enum FIRST
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(request.getMethod().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        // 3. NOW you can use method safely
        if (method == PaymentMethod.CREDIT_CARD &&
                (request.getCardLastFour() == null || !request.getCardLastFour().matches("\\d{4}"))) {
            return ResponseEntity.badRequest().build();
        }
        // 4. Call service
        ticketSaleService.processTicketSale(
                bookingId,
                method,
                request.getCardLastFour()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
