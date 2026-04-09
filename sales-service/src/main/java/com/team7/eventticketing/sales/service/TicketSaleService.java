package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.dto.RevenueReportDTO;
import com.team7.eventticketing.sales.dto.SaleDetailsDTO;
import com.team7.eventticketing.sales.dto.TicketSaleDTO;
import com.team7.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team7.eventticketing.sales.model.*;
import com.team7.eventticketing.sales.repository.PromotionRepository;
import com.team7.eventticketing.sales.repository.SalePromotionRepository;
import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TicketSaleService {

    @Autowired
    private TicketSaleRepository ticketSaleRepository;
    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    private SalePromotionRepository  salePromotionRepository;
    @Autowired
    private SalePromotionService salePromotionService;

    public TicketSaleDTO save(TicketSaleDTO ticketSaleDTO) {
        TicketSale ticketSale = convertToEntity(ticketSaleDTO);
        if (ticketSale.getCreatedAt() == null) {
            ticketSale.setCreatedAt(LocalDateTime.now());
        }
        return convertToDTO(ticketSaleRepository.save(ticketSale));
    }

    public Optional<TicketSaleDTO> findById(Long id) {
        return ticketSaleRepository.findById(id).map(this::convertToDTO);
    }

    public List<TicketSaleDTO> findAll() {
        return ticketSaleRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public void deleteById(Long id) {
        ticketSaleRepository.deleteById(id);
    }

    public TicketSaleDTO convertToDTO(TicketSale ticketSale) {
        TicketSaleDTO dto = new TicketSaleDTO();
        dto.setId(ticketSale.getId());
        dto.setBookingId(ticketSale.getBookingId());
        dto.setUserId(ticketSale.getUserId());
        dto.setAmount(ticketSale.getAmount());
        dto.setMethod(ticketSale.getMethod());
        dto.setStatus(ticketSale.getStatus());
        dto.setTransactionDetails(ticketSale.getTransactionDetails());
        dto.setCreatedAt(ticketSale.getCreatedAt());
        if (ticketSale.getSalePromotions() != null) {
            dto.setSalePromotions(
                    ticketSale.getSalePromotions().stream()
                            .map(salePromotionService::convertToDTO)
                            .toList()
            );
        }
        return dto;
    }

    public TicketSale convertToEntity(TicketSaleDTO dto) {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(dto.getId());
        ticketSale.setBookingId(dto.getBookingId());
        ticketSale.setUserId(dto.getUserId());
        ticketSale.setAmount(dto.getAmount());
        ticketSale.setMethod(dto.getMethod());
        ticketSale.setStatus(dto.getStatus());
        ticketSale.setTransactionDetails(dto.getTransactionDetails());
        ticketSale.setCreatedAt(dto.getCreatedAt());
        return ticketSale;
    }

    public List<TicketSaleDTO> searchTicketSales(TicketSaleStatus status,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate must be before or equal to endDate"
            );
        }

        LocalDateTime startDateTime = (startDate != null)
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);

        LocalDateTime endDateTime = (endDate != null)
                ? endDate.atTime(23, 59, 59)
                : LocalDateTime.of(9999, 12, 31, 23, 59, 59);

        TicketSaleStatus effectiveStatus = (status != null)
                ? status
                : TicketSaleStatus.PENDING;

        return ticketSaleRepository.searchTicketSales(
                        status == null,
                        effectiveStatus,
                        startDate == null,
                        startDateTime,
                        endDate == null,
                        endDateTime
                )
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional
    public TicketSale applyPromotionToSale(Long saleId, Long promotionId) {
        TicketSale sale = ticketSaleRepository.findById(saleId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
        if (sale.getStatus() != TicketSaleStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cannot apply promotion to a completed/cancelled sale"
            );
        }
        Promotion promo = promotionRepository.findById(promotionId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));
        if (!Boolean.TRUE.equals(promo.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion is inactive");
        }
        if (promo.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion is expired");
        }
        if (promo.getCurrentUses() >= promo.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion usage limit reached");
        }
        if (salePromotionRepository.existsByTicketSaleIdAndPromotionId(saleId, promotionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "promotion already applied");
        }
        double discount;
        if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
            discount= sale.getAmount() * promo.getDiscountValue() / 100;

        }
        else {
            discount= promo.getDiscountValue();
        }
        if (discount>sale.getAmount() ){
            discount=sale.getAmount();
        }
        promo.setCurrentUses(promo.getCurrentUses()+1);
        promotionRepository.save(promo);
        SalePromotion salePromo = new SalePromotion();
        salePromo.setPromotion(promo);
        salePromo.setAppliedAt(LocalDateTime.now());
        salePromo.setDiscountApplied(discount);
        sale.addSalePromotion(salePromo);
        return ticketSaleRepository.save(sale);
    }
    @Transactional
    public void processTicketSale(Long bookingId, PaymentMethod method, String cardLastFour){
        boolean doesExist = ticketSaleRepository.bookingExists(bookingId);
        if (!doesExist){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");        }
        String bookingStatus = ticketSaleRepository.getBookingStatus(bookingId);
        if (!"COMPLETED".equalsIgnoreCase(bookingStatus)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must be COMPLETED");
        }
        TicketSale ticketSale = ticketSaleRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));

        if (ticketSale.getStatus() == TicketSaleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
        }
        if (method == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }
        ticketSale.setMethod(method);
        ticketSale.setStatus( TicketSaleStatus.COMPLETED);
        java.util.Map<String, Object> transactionDetails = ticketSale.getTransactionDetails();

        if (transactionDetails == null) {
            transactionDetails = new HashMap<>();
        }
        transactionDetails.put("gatewayResponse", "approved");
        transactionDetails.put("bookingReference", bookingId);
        if (cardLastFour != null) {
            transactionDetails.put("cardLastFour", cardLastFour);
        }
        ticketSale.setTransactionDetails(transactionDetails);
        ticketSaleRepository.save(ticketSale);
    }
    public UserSaleSummaryDTO getUserSaleSummary(Long userId) {
       boolean userExists = ticketSaleRepository.userExists(userId);

        if (!userExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        List<Object[]> rows = ticketSaleRepository.getUserSalesSummaryByMethod(
                userId,
                TicketSaleStatus.COMPLETED
        );

        Map<String, Double> methodBreakdown = new HashMap<>();
        int totalSales = 0;
        double totalAmount = 0.0;

        for (Object[] row : rows) {
            PaymentMethod method = (PaymentMethod) row[0];
            Long count = (Long) row[1];
            Double amount = ((Number) row[2]).doubleValue();

            methodBreakdown.put(method.name(), amount);
            totalSales += count.intValue();
            totalAmount += amount;
        }

        return new UserSaleSummaryDTO(
                userId,
                totalSales,
                totalAmount,
                methodBreakdown
        );
    }

    @Transactional
    public TicketSaleDTO processRefund(Long saleId, String reason) {
        TicketSale ticketSale = ticketSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ticket sale not found"));

        if (ticketSale.getStatus() != TicketSaleStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only COMPLETED ticket sales can be refunded"
            );
        }

        ticketSale.setStatus(TicketSaleStatus.REFUNDED);

        Map<String, Object> transactionDetails = ticketSale.getTransactionDetails();
        if (transactionDetails == null) {
            transactionDetails = new HashMap<>();
        }

        transactionDetails.put("refundReason", reason);
        transactionDetails.put("refundedAt", LocalDateTime.now().toString());

        ticketSale.setTransactionDetails(transactionDetails);

        TicketSale saved = ticketSaleRepository.save(ticketSale);
        return convertToDTO(saved);
    }

    public RevenueReportDTO getRevenueReport(LocalDateTime start, LocalDateTime end) {

        Double totalRevenue = ticketSaleRepository.getTotalRevenue(start, end);
        Long totalTransactions = ticketSaleRepository.getTotalTransactions(start, end);
        Double refundedAmount = ticketSaleRepository.getRefundedAmount(start, end);
        Long refundCount = ticketSaleRepository.getRefundCount(start, end);

        Double averageSale = (totalTransactions != 0)
                ? totalRevenue / totalTransactions
                : 0.0;

        return new RevenueReportDTO(
                totalRevenue,
                totalTransactions,
                averageSale,
                refundedAmount,
                refundCount
        );
    }
    @Transactional
    public TicketSale retryFailedSale(Long id) {
        TicketSale sale = ticketSaleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ticket sale not found"));

        if (sale.getStatus() != TicketSaleStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only FAILED ticket sales can be retried");
        }

        sale.setStatus(TicketSaleStatus.COMPLETED);

        Map<String, Object> details = sale.getTransactionDetails();
        if (details == null) {
            details = new HashMap<>();
        }

        int retryAttempt = 0;
        if (details.get("retryAttempt") instanceof Number n) {
            retryAttempt = n.intValue();
        }
        details.put("retryAttempt", retryAttempt + 1);
        details.put("gatewayResponse", "approved");

        sale.setTransactionDetails(details);

        return ticketSaleRepository.save(sale);
    }
    public SaleDetailsDTO getSaleDetails(Long saleId) {
        TicketSale sale = ticketSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ticket sale not found"));

        List<SaleDetailsDTO.AppliedPromotionDTO> appliedPromotions = sale.getSalePromotions()
                .stream()
                .map(sp -> new SaleDetailsDTO.AppliedPromotionDTO(
                        sp.getPromotion().getCode(),
                        sp.getPromotion().getDiscountType().name(),
                        sp.getDiscountApplied(),
                        sp.getAppliedAt()
                ))
                .toList();

        double totalDiscount = appliedPromotions.stream()
                .mapToDouble(SaleDetailsDTO.AppliedPromotionDTO::getDiscountApplied)
                .sum();

        SaleDetailsDTO dto = new SaleDetailsDTO();
        dto.setSaleId(sale.getId());
        dto.setBookingId(sale.getBookingId());
        dto.setUserId(sale.getUserId());
        dto.setOriginalAmount(sale.getAmount());
        dto.setMethod(sale.getMethod());
        dto.setStatus(sale.getStatus());
        dto.setTransactionDetails(sale.getTransactionDetails());
        dto.setAppliedPromotions(appliedPromotions);
        dto.setTotalDiscount(totalDiscount);
        dto.setFinalAmount(sale.getAmount() - totalDiscount);

        return dto;
    }

}
