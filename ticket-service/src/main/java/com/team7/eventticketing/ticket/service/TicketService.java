package com.team7.eventticketing.ticket.service;

import com.team7.eventticketing.ticket.dto.NearbyTicketDTO;
import com.team7.eventticketing.ticket.dto.IssueTicketDTO;
import com.team7.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import com.team7.eventticketing.ticket.dto.TicketDTO;
import com.team7.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.model.TicketStatus;
import com.team7.eventticketing.ticket.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

  @Transactional
	public int purgeOldTickets(int olderThanDays) {
		if (olderThanDays <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "olderThanDays must be greater than 0");
		}
		LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
		return ticketRepository.deleteOldExpiredOrCancelled(cutoff);
	}

	public List<NearbyTicketDTO> getNearbyTickets(double lat, double lon, double radiusKm) {
		if (radiusKm < 0) {
			throw new IllegalArgumentException("radiusKm must be non-negative");
		}
		List<Object[]> results = ticketRepository.findNearbyTicketsNative(lat, lon, radiusKm);
		return results.stream().map(row -> new NearbyTicketDTO(
				((Number) row[0]).longValue(),
				(String) row[1],
				((Number) row[2]).longValue(),
				(String) row[3],
				(Double) row[4],
				(Double) row[5],
				(Double) row[6])).toList();
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

  @Transactional(readOnly = true)
  public List<UnusedTicketDTO> getUnusedTicketsForUpcomingEvents() {
      return ticketRepository.findUnusedTicketsForUpcomingEvents();
  } 
}
