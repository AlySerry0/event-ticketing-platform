package com.team7.eventticketing.sales.controller;

import com.team7.eventticketing.sales.dto.*;
import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.model.TicketSaleStatus;
import com.team7.eventticketing.sales.service.TicketSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class TicketSaleController {

    @Autowired
    private TicketSaleService ticketSaleService;

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PostMapping
    public TicketSaleDTO create(@RequestBody TicketSaleDTO ticketSaleDTO) {
        return ticketSaleService.save(ticketSaleDTO);
    }
    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/{saleId}/details")
    public ResponseEntity<SaleDetailsDTO> getSaleDetails(@PathVariable Long saleId) {
        return ResponseEntity.ok(ticketSaleService.getSaleDetails(saleId));
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<TicketSaleDTO> getById(@PathVariable Long id) {
        return ticketSaleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping
    public List<TicketSaleDTO> getAll() {
        return ticketSaleService.findAll();
    }

    @GetMapping("/search")
    public List<TicketSaleDTO> searchTicketSales(
            @RequestParam(required = false) TicketSaleStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) {
        return ticketSaleService.searchTicketSales(status, startDate, endDate);
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
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

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (ticketSaleService.findById(id).isPresent()) {
            ticketSaleService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PostMapping("/{saleId}/promotions/{promotionId}")
    public ResponseEntity<TicketSaleDTO> applyPromotion(
            @PathVariable Long saleId,
            @PathVariable Long promotionId) {

        TicketSale updatedSale = ticketSaleService.applyPromotionToSale(saleId, promotionId);
        return ResponseEntity.ok(ticketSaleService.convertToDTO(updatedSale));
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<TicketSaleDTO> processTicketSale(
            @PathVariable Long bookingId,
            @RequestBody ProcessTicketDTO request
    ) {

        TicketSale updatedSale = ticketSaleService.processTicketSale(
                bookingId,
                request.getMethod(),
                request.getCardLastFour()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketSaleService.convertToDTO(updatedSale));
    }
    
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserSaleSummaryDTO> getUserSaleSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(ticketSaleService.getUserSaleSummary(userId));
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<TicketSaleDTO> processRefund(
            @PathVariable Long id,
            @RequestBody RefundRequestDTO request
    ) {
        TicketSaleDTO refundedSale = ticketSaleService.processRefund(id, request.getReason());
        return ResponseEntity.ok(refundedSale);
    }

    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/reports/revenue")
    public RevenueReportDTO getRevenueReport(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate cannot be after endDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        return ticketSaleService.getRevenueReport(start, end);

    }
    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @PutMapping("/{id}/retry")
    public ResponseEntity<TicketSaleDTO> retryFailedSale(@PathVariable Long id) {
        TicketSale updatedSale = ticketSaleService.retryFailedSale(id);
        return ResponseEntity.ok(ticketSaleService.convertToDTO(updatedSale));
    }
    @PreAuthorize("hasAnyRole('ATTENDEE', 'ADMIN')")
    @GetMapping("/analytics/tier")
    public ResponseEntity<List<TierRevenueDTO>> getTicketSalesByTier(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        ticketSaleService.logTierAnalyticsViewed(startDate, endDate);

        List<TierRevenueDTO> result = ticketSaleService.getTierRevenue(startDate, endDate);

        return ResponseEntity.ok(result);
    }
}

