package com.campuseventhub.event.mapper;

import com.campuseventhub.event.dto.EventCategorySummary;
import com.campuseventhub.event.dto.EventResponse;
import com.campuseventhub.event.entity.Event;

public final class EventMapper {

    private EventMapper() {
    }

    public static EventResponse toResponse(Event event) {
        EventResponse response = new EventResponse();
        response.setId(event.getId());
        response.setTitle(event.getTitle());
        response.setDescription(event.getDescription());
        response.setLocation(event.getLocation());
        response.setStartTime(event.getStartTime());
        response.setEndTime(event.getEndTime());
        response.setCapacity(event.getCapacity());
        response.setStatus(event.getStatus());
        response.setSubmittedBy(event.getSubmittedBy().getUsername());
        response.setReviewedBy(event.getReviewedBy() != null ? event.getReviewedBy().getUsername() : null);
        response.setReviewComment(event.getReviewComment());
        response.setReviewedAt(event.getReviewedAt());
        response.setCategories(event.getCategories().stream()
                .map(category -> new EventCategorySummary(category.getId(), category.getName()))
                .toList());
        response.setCreatedAt(event.getCreatedAt());
        response.setUpdatedAt(event.getUpdatedAt());
        return response;
    }
}
