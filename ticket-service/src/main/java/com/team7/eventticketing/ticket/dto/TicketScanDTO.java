package com.team7.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class TicketScanDTO {

    private LocalDateTime timestamp;
    private String scanType;
    private String attendeeName;
    private String gate;
    private String section;
    private String seatNumber;
    private String notes;

    public TicketScanDTO() {}

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }

    public String getAttendeeName() { return attendeeName; }
    public void setAttendeeName(String attendeeName) { this.attendeeName = attendeeName; }

    public String getGate() { return gate; }
    public void setGate(String gate) { this.gate = gate; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private LocalDateTime timestamp;
        private String scanType;
        private String attendeeName;
        private String gate;
        private String section;
        private String seatNumber;
        private String notes;

        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder scanType(String scanType) { this.scanType = scanType; return this; }
        public Builder attendeeName(String attendeeName) { this.attendeeName = attendeeName; return this; }
        public Builder gate(String gate) { this.gate = gate; return this; }
        public Builder section(String section) { this.section = section; return this; }
        public Builder seatNumber(String seatNumber) { this.seatNumber = seatNumber; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }

        public TicketScanDTO build() {
            TicketScanDTO dto = new TicketScanDTO();
            dto.setTimestamp(this.timestamp);
            dto.setScanType(this.scanType);
            dto.setAttendeeName(this.attendeeName);
            dto.setGate(this.gate);
            dto.setSection(this.section);
            dto.setSeatNumber(this.seatNumber);
            dto.setNotes(this.notes);
            return dto;
        }
    }
}