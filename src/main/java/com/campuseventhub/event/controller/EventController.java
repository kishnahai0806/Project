package com.campuseventhub.event.controller;

import com.campuseventhub.common.api.PagedResponse;
import com.campuseventhub.event.dto.CreateEventResult;
import com.campuseventhub.event.dto.EventCreateRequest;
import com.campuseventhub.event.dto.EventResponse;
import com.campuseventhub.event.dto.EventReviewRequest;
import com.campuseventhub.event.dto.EventUpdateRequest;
import com.campuseventhub.event.dto.WeeklyEventMetricResponse;
import com.campuseventhub.event.entity.EventStatus;
import com.campuseventhub.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Event submission, moderation, and discovery")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit a new event (idempotent)")
    public ResponseEntity<EventResponse> create(
            @Valid @RequestBody EventCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        CreateEventResult result = eventService.createEvent(request, authentication.getName(), idempotencyKey);
        return ResponseEntity.status(result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("X-Idempotent-Replay", String.valueOf(result.isReplayed()))
                .body(result.getEvent());
    }

    @GetMapping
    @Operation(summary = "Browse/search events with filters")
    public ResponseEntity<PagedResponse<EventResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime weekStart,
            @PageableDefault(size = 10, sort = "startTime") Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(eventService.searchEvents(q, status, categoryId, startFrom, startTo, weekStart, pageable, authentication));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get event details by id")
    public ResponseEntity<EventResponse> getById(@PathVariable UUID eventId, Authentication authentication) {
        return ResponseEntity.ok(eventService.getEvent(eventId, authentication));
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update event")
    public ResponseEntity<EventResponse> update(@PathVariable UUID eventId,
                                                @Valid @RequestBody EventUpdateRequest request,
                                                Authentication authentication) {
        return ResponseEntity.ok(eventService.updateEvent(eventId, request, authentication.getName()));
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel event")
    public ResponseEntity<Void> delete(@PathVariable UUID eventId, Authentication authentication) {
        eventService.deleteEvent(eventId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve event (ADMIN)")
    public ResponseEntity<EventResponse> approve(@PathVariable UUID eventId,
                                                 @Valid @RequestBody(required = false) EventReviewRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(eventService.reviewEvent(eventId, request, true, authentication.getName()));
    }

    @PostMapping("/{eventId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject event (ADMIN)")
    public ResponseEntity<EventResponse> reject(@PathVariable UUID eventId,
                                                @Valid @RequestBody(required = false) EventReviewRequest request,
                                                Authentication authentication) {
        return ResponseEntity.ok(eventService.reviewEvent(eventId, request, false, authentication.getName()));
    }

    @GetMapping("/metrics/weekly")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get weekly aggregated event metrics by status (ADMIN)")
    public ResponseEntity<List<WeeklyEventMetricResponse>> weeklyMetrics(
            @Parameter(description = "Start datetime (inclusive)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "End datetime (inclusive)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(eventService.weeklyMetrics(from, to));
    }
}
