package com.team7.eventticketing.sales.controller;

import com.team7.eventticketing.sales.dto.TicketSaleDTO;
import com.team7.eventticketing.sales.model.PaymentMethod;
import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.service.TicketSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
            @RequestBody Map<String, Object> body
    ) {
        PaymentMethod method = PaymentMethod.valueOf((String) body.get("method"));
        String cardLastFour = (String) body.get("cardLastFour");

        ticketSaleService.processTicketSale(bookingId, method, cardLastFour);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
