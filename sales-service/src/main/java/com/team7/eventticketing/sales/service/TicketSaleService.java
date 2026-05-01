package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.sales.observer.EntitySubject;
import com.team7.eventticketing.sales.util.CacheInvalidationService;
import com.team7.eventticketing.sales.factory.EventFactory;
import com.team7.eventticketing.sales.dto.RevenueReportDTO;
import com.team7.eventticketing.sales.dto.SaleDetailsDTO;
import com.team7.eventticketing.sales.dto.TicketSaleDTO;
import com.team7.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team7.eventticketing.sales.model.*;
import com.team7.eventticketing.sales.repository.PromotionRepository;
import com.team7.eventticketing.sales.repository.SalePromotionRepository;
import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import com.team7.eventticketing.sales.adapter.MongoDocumentAdapter;
import com.team7.eventticketing.sales.dto.SaleAuditTrailDTO;
import com.team7.eventticketing.sales.repository.PaymentAuditEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team7.eventticketing.sales.model.PaymentAuditEvent;
import com.team7.eventticketing.sales.observer.EntityObserver;
import com.team7.eventticketing.sales.observer.MongoEventLogger;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.team7.eventticketing.sales.observer.EntityObserver;
import com.team7.eventticketing.sales.observer.MongoEventLogger;
import com.team7.eventticketing.sales.util.CacheInvalidationService;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;

import com.team7.eventticketing.sales.dto.RefundRequestDTO;
import com.team7.eventticketing.sales.strategy.RefundResult;
import com.team7.eventticketing.sales.strategy.RefundStrategy;
import com.team7.eventticketing.sales.strategy.RefundStrategySelector;
import java.time.Duration;

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
    @Autowired
    private PaymentAuditEventRepository paymentAuditEventRepository;
    @Autowired
    private MongoDocumentAdapter mongoDocumentAdapter;
    @Autowired
    private CacheInvalidationService cacheInvalidationService;
    @Autowired
    private MongoEventLogger mongoEventLogger;
    @Autowired
    private EntitySubject entitySubject;
    @Autowired
    private RefundStrategySelector refundStrategySelector;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void initObservers() {
        registerObserver(mongoEventLogger);
    }

    public void registerObserver(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(EntityObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }
    public TicketSaleDTO save(TicketSaleDTO ticketSaleDTO) {
        TicketSale ticketSale = convertToEntity(ticketSaleDTO);
        if (ticketSale.getCreatedAt() == null) {
            ticketSale.setCreatedAt(LocalDateTime.now());
        }
        TicketSale saved = ticketSaleRepository.save(ticketSale);
        invalidateAfterTicketSaleWrite(saved);
        return convertToDTO(saved);
    }
    @Cacheable(value = "ticket-sale", key = "#id")
    public Optional<TicketSaleDTO> findById(Long id) {
        return ticketSaleRepository.findById(id).map(this::convertToDTO);
    }
    public List<TicketSaleDTO> findAll() {
        return ticketSaleRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public void deleteById(Long id) {
        TicketSale sale = ticketSaleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket sale not found"
                ));

        ticketSaleRepository.deleteById(id);

        invalidateAfterTicketSaleWrite(sale);
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

    @Cacheable(
            value = "S5-F1",
            key = "#status + '|' + #startDate + '|' + #endDate"
    )
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
    private Map<String, Object> buildAuditPayload(TicketSale sale) {
        Map<String, Object> details = new HashMap<>();

        if (sale.getTransactionDetails() != null) {
            details.putAll(sale.getTransactionDetails());
        }

        details.put("status", sale.getStatus() != null ? sale.getStatus().name() : null);
        details.put("bookingId", sale.getBookingId());
        details.put("userId", sale.getUserId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("saleId", sale.getId());
        payload.put("method", sale.getMethod() != null ? sale.getMethod().name() : null);
        payload.put("amount", sale.getAmount());
        payload.put("details", details);

        return payload;
    }

    private void invalidateAfterTicketSaleWrite(TicketSale sale) {
        Long saleId = sale.getId();
        Long userId = sale.getUserId();

        // CRUD / entity reads
        cacheInvalidationService.invalidateCacheWildcard("sales-service::ticket-sale::" + saleId);

        // S5-F1: search ticket sales
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F1::*");

        // S5-F3: user ticket sale summary
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F3::" + userId);

        // S5-F6: revenue report
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F6::*");

        // S5-F8: sale details
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F8::" + saleId);

        // S5-F10:
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F10::*");

        // S5-F11: audit trail
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F11::" + saleId);
    }


    @Transactional
    public TicketSale applyPromotionToSale(Long saleId, Long promotionId) {
        TicketSale sale = ticketSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));

        if (sale.getStatus() != TicketSaleStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion can only be applied to PENDING sales");
        }

        Promotion promo = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));

        if (!Boolean.TRUE.equals(promo.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion is inactive");
        }

        if (promo.getExpiryDate() == null || !promo.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion is expired");
        }

        int currentUses = promo.getCurrentUses() != null ? promo.getCurrentUses() : 0;
        if (currentUses >= promo.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion usage limit reached");
        }

        if (salePromotionRepository.existsByTicketSaleIdAndPromotionId(saleId, promotionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "promotion already applied");
        }

        double discount = promo.getDiscountType() == DiscountType.PERCENTAGE
                ? sale.getAmount() * (promo.getDiscountValue() / 100.0)
                : promo.getDiscountValue();

        double alreadyAppliedDiscount = 0.0;
        if (sale.getSalePromotions() != null) {
            alreadyAppliedDiscount = sale.getSalePromotions().stream()
                    .mapToDouble(SalePromotion::getDiscountApplied)
                    .sum();
        }

        double maxAvailableDiscount = Math.max(0.0, sale.getAmount() - alreadyAppliedDiscount);
        discount = Math.min(discount, maxAvailableDiscount);
        discount = Math.round(discount * 100.0) / 100.0;

        SalePromotion salePromo = new SalePromotion();
        salePromo.setPromotion(promo);
        salePromo.setDiscountApplied(discount);
        salePromo.setAppliedAt(LocalDateTime.now());

        sale.addSalePromotion(salePromo);

        promo.setCurrentUses(currentUses + 1);
        promotionRepository.saveAndFlush(promo);

        TicketSale saved = ticketSaleRepository.saveAndFlush(sale);

        Map<String, Object> payload = buildAuditPayload(saved);
        Map<String, Object> details = (Map<String, Object>) payload.get("details");
        details.put("promotionCode", promo.getCode());
        details.put("discountApplied", discount);

        entitySubject.notifyObservers("PROMOTION_APPLIED", payload);

        invalidateAfterTicketSaleWrite(saved);
        return saved;
    }

    @Transactional
    public TicketSale processTicketSale(Long bookingId, String methodStr, String cardLastFour, boolean simulateFailure) {
        boolean doesExist = ticketSaleRepository.bookingExists(bookingId);
        if (!doesExist) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        String bookingStatus = ticketSaleRepository.getBookingStatus(bookingId);
        if (!"COMPLETED".equalsIgnoreCase(bookingStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must be COMPLETED");
        }

        TicketSale ticketSale = ticketSaleRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));

        if (ticketSale.getStatus() == TicketSaleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
        }

        if (methodStr == null || methodStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }

        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(methodStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
        }

        if (method == PaymentMethod.CREDIT_CARD &&
                (cardLastFour == null || !cardLastFour.matches("\\d{4}"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardLastFour must be 4 digits");
        }

        ticketSale.setMethod(method);

        Map<String, Object> transactionDetails = ticketSale.getTransactionDetails();
        if (transactionDetails == null) {
            transactionDetails = new HashMap<>();
        }

        transactionDetails.put("bookingReference", bookingId);
        transactionDetails.put("method", method.name());

        if (cardLastFour != null) {
            transactionDetails.put("cardLastFour", cardLastFour);
        }

        ticketSale.setStatus(TicketSaleStatus.PENDING);
        ticketSale.setTransactionDetails(transactionDetails);

        TicketSale pendingSale = ticketSaleRepository.saveAndFlush(ticketSale);
        entitySubject.notifyObservers("CREATED", buildAuditPayload(pendingSale));

        if (simulateFailure) {
            transactionDetails.put("gatewayResponse", "declined");
            transactionDetails.put("failureReason", "Simulated payment failure");

            pendingSale.setStatus(TicketSaleStatus.FAILED);
            pendingSale.setTransactionDetails(transactionDetails);

            TicketSale failedSale = ticketSaleRepository.saveAndFlush(pendingSale);
            entitySubject.notifyObservers("FAILED", buildAuditPayload(failedSale));

            invalidateAfterTicketSaleWrite(failedSale);
            return failedSale;
        }

        transactionDetails.put("gatewayResponse", "approved");

        pendingSale.setStatus(TicketSaleStatus.COMPLETED);
        pendingSale.setTransactionDetails(transactionDetails);

        TicketSale completedSale = ticketSaleRepository.saveAndFlush(pendingSale);
        entitySubject.notifyObservers("COMPLETED", buildAuditPayload(completedSale));
        invalidateAfterTicketSaleWrite(completedSale);
        return completedSale;
    }

    @Cacheable(
            value = "S5-F3",
            key = "#userId"
    )
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
    @Transactional
    public TicketSaleDTO processRefundWithWindowPolicy(Long saleId, RefundRequestDTO request) {
        TicketSale ticketSale = ticketSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ticket sale not found"));

        if (ticketSale.getStatus() != TicketSaleStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only COMPLETED ticket sales can be refunded"
            );
        }

        LocalDateTime eventDate = ticketSaleRepository.findEventDateBySaleId(saleId);

        if (eventDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "booking has no associated event"
            );
        }

        long hoursUntilEvent = Duration.between(LocalDateTime.now(), eventDate).toHours();

        RefundStrategy strategy = refundStrategySelector.select(ticketSale, eventDate);
        RefundResult refundResult = strategy.calculateRefund(ticketSale, request, eventDate);

        if (!refundResult.isApproved()) {
            Map<String, Object> detailsPayload = new HashMap<>();
            detailsPayload.put("strategyName", refundResult.getStrategyName());
            detailsPayload.put("denialReason", "refund window expired");
            detailsPayload.put("hoursUntilEvent", hoursUntilEvent);

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("saleId", ticketSale.getId());
            eventPayload.put("method", ticketSale.getMethod() != null ? ticketSale.getMethod().name() : null);
            eventPayload.put("amount", ticketSale.getAmount());
            eventPayload.put("details", detailsPayload);

            entitySubject.notifyObservers("REFUND_DENIED", eventPayload);

            cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F10::*");
            cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F11::" + ticketSale.getId());

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refund window expired");
        }

        ticketSale.setStatus(TicketSaleStatus.REFUNDED);

        Map<String, Object> transactionDetails = ticketSale.getTransactionDetails();
        if (transactionDetails == null) {
            transactionDetails = new HashMap<>();
        }

        transactionDetails.put("refundAmount", refundResult.getRefundAmount());
        transactionDetails.put("refundPolicy", refundResult.getStrategyName());
        transactionDetails.put("refundReason", request.getReason());
        transactionDetails.put("refundedAt", LocalDateTime.now().toString());

        ticketSale.setTransactionDetails(transactionDetails);

        TicketSale saved = ticketSaleRepository.save(ticketSale);

        Map<String, Object> detailsPayload = new HashMap<>();
        detailsPayload.put("strategyName", refundResult.getStrategyName());
        detailsPayload.put("reason", request.getReason());
        detailsPayload.put("originalAmount", saved.getAmount());
        detailsPayload.put("refundAmount", refundResult.getRefundAmount());
        detailsPayload.put("hoursUntilEvent", hoursUntilEvent);

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("saleId", saved.getId());
        eventPayload.put("method", saved.getMethod() != null ? saved.getMethod().name() : null);
        eventPayload.put("amount", saved.getAmount());
        eventPayload.put("details", detailsPayload);

        entitySubject.notifyObservers("REFUNDED", eventPayload);

        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F11::" + saved.getId());
        cacheInvalidationService.invalidateCacheWildcard("sales-service::ticket-sale::" + saved.getId());

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
        details.put("retriedAt", LocalDateTime.now().toString());

        sale.setTransactionDetails(details);

        TicketSale savedSale = ticketSaleRepository.save(sale);

        entitySubject.notifyObservers("RETRY_ATTEMPTED", buildAuditPayload(savedSale));
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F8::" + savedSale.getId());
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("sales-service::S5-F11::" + savedSale.getId());
        cacheInvalidationService.invalidateCacheWildcard("sales-service::ticket-sale::" + savedSale.getId());

        return savedSale;
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
    @Cacheable(value = "S5-F11", key = "#saleId")
    public SaleAuditTrailDTO getSaleAuditTrail(Long saleId) {

        if (!ticketSaleRepository.existsById(saleId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket sale not found"
            );
        }

        List<PaymentAuditEvent> events =
                paymentAuditEventRepository.findBySaleIdAndActionNotOrderByTimestampAsc(
                        saleId,
                        "ANALYTICS_VIEWED"
                );

        return mongoDocumentAdapter.adapt(saleId, events);
    }
}
