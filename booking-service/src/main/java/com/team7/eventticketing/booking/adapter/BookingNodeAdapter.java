package com.team7.eventticketing.booking.adapter;

import com.team7.eventticketing.booking.model.neo4j.EventNode;
import com.team7.eventticketing.booking.model.neo4j.UserNode;
import org.springframework.stereotype.Component;

/**
 * Adapter Pattern (Milestone 2 Enforcement)
 * Converts Native SQL Object[] results into domain nodes.
 */
@Component
public class BookingNodeAdapter {

    /**
     * Adapts Native SQL user name result to a UserNode.
     */
    public UserNode toUserNode(Long userId, String name) {
        UserNode userNode = new UserNode();
        userNode.setUserId(userId);
        userNode.setName(name != null ? name : "Unknown User");
        return userNode;
    }

    /**
     * Adapts Native SQL event details result to an EventNode.
     * Expected row order: [0] name, [1] category, [2] eventDate
     */
    public EventNode toEventNode(Long eventId, Object[] details) {
        EventNode eventNode = new EventNode();
        eventNode.setEventId(eventId);
        if (details != null && details.length >= 2) {
            eventNode.setName((String) details[0]);
            eventNode.setCategory(details[1] != null ? details[1].toString() : "UNSPECIFIED");
        } else {
            eventNode.setName("Unknown Event");
            eventNode.setCategory("UNSPECIFIED");
        }
        return eventNode;
    }
}
