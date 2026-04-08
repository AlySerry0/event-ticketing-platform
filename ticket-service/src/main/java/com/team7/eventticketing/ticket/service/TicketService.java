package com.team7.eventticketing.ticket.service;

import com.team7.eventticketing.ticket.dto.IssueTicketDTO;
import com.team7.eventticketing.ticket.dto.TicketDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.model.TicketStatus;
import com.team7.eventticketing.ticket.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    public TicketDTO save(TicketDTO ticketDTO) {
        Ticket ticket = convertToEntity(ticketDTO);
        return convertToDTO(ticketRepository.save(ticket));
    }

    public Optional<TicketDTO> findById(Long id) {
        return ticketRepository.findById(id).map(this::convertToDTO);
    }

    public List<TicketDTO> findAll() {
        return ticketRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public void deleteById(Long id) {
        ticketRepository.deleteById(id);
    }

    public TicketDTO convertToDTO(Ticket ticket) {
        TicketDTO dto = new TicketDTO();
        dto.setId(ticket.getId());
        dto.setBookingId(ticket.getBookingId());
        dto.setAttendeeName(ticket.getAttendeeName());
        dto.setTicketCode(ticket.getTicketCode());
        dto.setStatus(ticket.getStatus());
        dto.setIssuedAt(ticket.getIssuedAt());
        dto.setMetadata(ticket.getMetadata());
        return dto;
    }

    public Ticket convertToEntity(TicketDTO dto) {
        Ticket ticket = new Ticket();
        ticket.setId(dto.getId());
        ticket.setBookingId(dto.getBookingId());
        ticket.setAttendeeName(dto.getAttendeeName());
        ticket.setTicketCode(dto.getTicketCode());
        ticket.setStatus(dto.getStatus());
        ticket.setIssuedAt(dto.getIssuedAt());
        ticket.setMetadata(dto.getMetadata());
        return ticket;
    }

    @Transactional
    public int purgeOldTickets(int olderThanDays) {
        if (olderThanDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "olderThanDays must be greater than 0");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        return ticketRepository.deleteOldExpiredOrCancelled(cutoff);
    }

    @Transactional
    public TicketDTO issueTicket(Long bookingId, IssueTicketDTO request) {
        if (!ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        Ticket ticket = new Ticket();
        ticket.setBookingId(bookingId);
        ticket.setAttendeeName(request.getAttendeeName());
        ticket.setTicketCode(request.getTicketCode());
        ticket.setMetadata(request.getMetadata());
        ticket.setStatus(TicketStatus.VALID);
        ticket.setIssuedAt(LocalDateTime.now());

        return convertToDTO(ticketRepository.save(ticket));
    }

    public TicketDTO getLatestTicketForBooking(Long bookingId) {
        if (!ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return ticketRepository.findFirstByBookingIdOrderByIssuedAtDesc(bookingId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for booking"));
    }
}