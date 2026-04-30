package com.team7.eventticketing.event.adapter;

import com.team7.eventticketing.event.dto.EventDashboardDTO;
import com.team7.eventticketing.event.dto.EventRevenueDTO;
import com.team7.eventticketing.event.dto.TopEventDTO;

public class ObjectArrayDtoAdapter {

    private final EventRevenueAdapter eventRevenueAdapter = new EventRevenueAdapter();
    private final TopEventAdapter topEventAdapter = new TopEventAdapter();
    private final EventDashboardAdapter eventDashboardAdapter=new EventDashboardAdapter();
    public EventRevenueDTO toEventRevenueDTO(Object[] row, Long eventId, String eventName) {
        return eventRevenueAdapter.adapt(row, eventId, eventName);
    }

    public TopEventDTO toTopEventDTO(Object[] row) {
        return topEventAdapter.adapt(row);
    }
    public EventDashboardDTO toEventDashboardDTO(Object[] row, Long eventId,
                                                 String name, Double rating) {
        return eventDashboardAdapter.adapt(row, eventId, name, rating);
    }
}