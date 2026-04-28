package com.team7.eventticketing.event.adapter;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.team7.eventticketing.event.elasticsearch.EventSearchDocument;

import java.util.Map;
import java.util.HashMap;

/**
 * Adapter Pattern — converts an Elasticsearch {@link Hit<EventSearchDocument>}
 * (returned by the ES Java client) into a usable DTO or Map.
 *
 * Used by S2-F10 (Full-Text Event Search) — whoever implements S2-F10
 * should inject or instantiate this adapter and call adaptToMap() or
 * adaptToSearchDocument() on each hit in the search response.
 *
 * Usage in S2-F10 service:
 *
 *   ElasticsearchHitAdapter adapter = new ElasticsearchHitAdapter();
 *
 *   SearchResponse<EventSearchDocument> response = esClient.search(...);
 *   List<EventSearchDocument> results = response.hits().hits()
 *       .stream()
 *       .map(adapter::adaptToSearchDocument)
 *       .toList();
 *
 * The adapter is intentionally thin — it does NOT call PostgreSQL.
 * If S2-F10 needs to enrich results with PG data (e.g. live rating),
 * do that in the service layer after adapting.
 */
public class ElasticsearchHitAdapter {

    /**
     * Extracts the typed source document from an ES hit.
     * Returns null if the hit has no source.
     *
     * @param hit a single Elasticsearch hit of type EventSearchDocument
     * @return the deserialized EventSearchDocument, or null
     */
    public EventSearchDocument adaptToSearchDocument(Hit<EventSearchDocument> hit) {
        if (hit == null) return null;
        return hit.source();   // Spring ES client already deserializes this for us
    }

    /**
     * Converts an ES hit to a plain Map — useful for lightweight responses
     * that don't need a full typed DTO, or for debugging.
     *
     * Returned keys: id, name, category, venue, description,
     *                eventDate, rating, status, score
     *
     * @param hit a single Elasticsearch hit of type EventSearchDocument
     * @return Map representation (never null)
     */
    public Map<String, Object> adaptToMap(Hit<EventSearchDocument> hit) {
        if (hit == null) return Map.of();

        EventSearchDocument doc = hit.source();
        if (doc == null) return Map.of();

        Map<String, Object> map = new HashMap<>();
        map.put("id",          doc.getId());
        map.put("name",        doc.getName());
        map.put("category",    doc.getCategory());
        map.put("venue",       doc.getVenue());
        map.put("description", doc.getDescription());
        map.put("eventDate",   doc.getEventDate());
        map.put("rating",      doc.getRating());
        map.put("status",      doc.getStatus());
        map.put("score",       hit.score());   // ES relevance score
        return map;
    }

    // -----------------------------------------------------------------------
    // Extension point for S2-F10 implementor:
    //
    // Add a typed adapt method here when you define a search result DTO, e.g.:
    //
    //   public EventSearchResultDTO adapt(Hit<EventSearchDocument> hit) {
    //       EventSearchDocument doc = hit.source();
    //       return EventSearchResultDTO.builder()
    //           .id(doc.getId())
    //           .name(doc.getName())
    //           .category(doc.getCategory())
    //           .venue(doc.getVenue())
    //           .description(doc.getDescription())
    //           .eventDate(doc.getEventDate())
    //           .rating(doc.getRating())
    //           .status(doc.getStatus())
    //           .relevanceScore(hit.score())
    //           .build();
    //   }
    // -----------------------------------------------------------------------
}