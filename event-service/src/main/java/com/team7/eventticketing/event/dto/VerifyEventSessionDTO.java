package com.team7.eventticketing.event.dto;

public class VerifyEventSessionDTO {

    private Long verifiedBy;

    public VerifyEventSessionDTO() {
    }

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Long verifiedBy) {
        this.verifiedBy = verifiedBy;
    }
}