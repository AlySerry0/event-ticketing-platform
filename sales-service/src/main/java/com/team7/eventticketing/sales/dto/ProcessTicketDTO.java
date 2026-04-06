package com.team7.eventticketing.sales.dto;

public class ProcessTicketDTO {
    private String method;
    private String cardLastFour;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public void setCardLastFour(String cardLastFour) {
        this.cardLastFour = cardLastFour;
    }
}
