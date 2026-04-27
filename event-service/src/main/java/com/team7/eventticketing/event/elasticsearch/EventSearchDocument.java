package com.team7.eventticketing.event.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch index document for the "events" index.
 *
 * Used by:
 *   S2-F10 — Full-Text Event Search (reads from ES)
 *   S2-F11 — Index Event for Search  (writes to ES)
 *
 * Field mapping per M2 spec Section 7.2.1:
 *   id          → Keyword  (exact match, corresponds to PG id)
 *   name        → Text     (full-text search, analyzed)
 *   category    → Keyword  (exact filter: CONCERT/SPORTS/THEATER/CONFERENCE/FESTIVAL)
 *   venue       → Text     (full-text search, analyzed)
 *   description → Text     (full-text search — from Event.details.description)
 *   eventDate   → Date     (range filtering)
 *   rating      → Double   (range filtering)
 *   status      → Keyword  (exact filter: UPCOMING/ONGOING/COMPLETED/CANCELLED)
 */
@Document(indexName = "events", createIndex = false)
public class EventSearchDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Text)
    private String venue;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Date)
    private LocalDateTime eventDate;

    @Field(type = FieldType.Double)
    private Double rating;

    @Field(type = FieldType.Keyword)
    private String status;

    public EventSearchDocument() {}

    public EventSearchDocument(Long id, String name, String category, String venue,
                               String description, LocalDateTime eventDate,
                               Double rating, String status) {
        this.id          = id;
        this.name        = name;
        this.category    = category;
        this.venue       = venue;
        this.description = description;
        this.eventDate   = eventDate;
        this.rating      = rating;
        this.status      = status;
    }

    // Getters and setters
    public Long getId()                  { return id; }
    public void setId(Long id)           { this.id = id; }

    public String getName()              { return name; }
    public void setName(String name)     { this.name = name; }

    public String getCategory()              { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getVenue()             { return venue; }
    public void setVenue(String venue)   { this.venue = venue; }

    public String getDescription()                   { return description; }
    public void setDescription(String description)   { this.description = description; }

    public LocalDateTime getEventDate()              { return eventDate; }
    public void setEventDate(LocalDateTime eventDate){ this.eventDate = eventDate; }

    public Double getRating()                { return rating; }
    public void setRating(Double rating)     { this.rating = rating; }

    public String getStatus()                { return status; }
    public void setStatus(String status)     { this.status = status; }
}