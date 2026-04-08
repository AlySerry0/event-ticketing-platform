package com.team7.eventticketing.ticket.service;

import com.team7.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import com.team7.eventticketing.ticket.dto.TicketDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
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

	public EventAttendanceSummaryDTO getEventSummary(Long eventId) {

		List<Object[]> results = ticketRepository.getEventAttendanceSummary(eventId);
		if (results == null || results.isEmpty()) {
			throw new RuntimeException("No tickets found");
		}
		Object[] row = results.get(0);
		long total = row[0] != null ? ((Number) row[0]).longValue() : 0;
		if (total == 0) {
			throw new RuntimeException("No tickets found");
		}
		long used = row[1] != null ? ((Number) row[1]).longValue() : 0;
		long valid = row[2] != null ? ((Number) row[2]).longValue() : 0;
		double attendanceRate = (used * 100.0) / total;
		LocalDateTime lastCheckIn = null;
		if (row[3] != null) {
			if (row[3] instanceof java.sql.Timestamp ts) {
				lastCheckIn = ts.toLocalDateTime();
			} else if (row[3] instanceof LocalDateTime ldt) {
				lastCheckIn = ldt;
			}
		}
		return new EventAttendanceSummaryDTO(
				eventId,
				total,
				used,
				valid,
				attendanceRate,
				lastCheckIn
		);
	}
}
