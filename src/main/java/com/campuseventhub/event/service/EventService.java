package com.campuseventhub.event.service;

import com.campuseventhub.category.entity.Category;
import com.campuseventhub.category.repository.CategoryRepository;
import com.campuseventhub.common.api.PagedResponse;
import com.campuseventhub.common.exception.BadRequestException;
import com.campuseventhub.common.exception.ConflictException;
import com.campuseventhub.common.exception.ForbiddenOperationException;
import com.campuseventhub.common.exception.ResourceNotFoundException;
import com.campuseventhub.event.dto.CreateEventResult;
import com.campuseventhub.event.dto.EventCreateRequest;
import com.campuseventhub.event.dto.EventResponse;
import com.campuseventhub.event.dto.EventReviewRequest;
import com.campuseventhub.event.dto.EventUpdateRequest;
import com.campuseventhub.event.dto.WeeklyEventMetricResponse;
import com.campuseventhub.event.entity.Event;
import com.campuseventhub.event.entity.EventStatus;
import com.campuseventhub.event.mapper.EventMapper;
import com.campuseventhub.event.repository.EventRepository;
import com.campuseventhub.event.repository.EventSpecifications;
import com.campuseventhub.idempotency.entity.IdempotencyRecord;
import com.campuseventhub.idempotency.repository.IdempotencyRecordRepository;
import com.campuseventhub.user.entity.Role;
import com.campuseventhub.user.entity.User;
import com.campuseventhub.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EventService {

    private static final String CREATE_EVENT_OPERATION = "CREATE_EVENT";

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public EventService(EventRepository eventRepository,
                        UserRepository userRepository,
                        CategoryRepository categoryRepository,
                        IdempotencyRecordRepository idempotencyRecordRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public CreateEventResult createEvent(EventCreateRequest request, String username, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }

        User actor = findUser(username);
        String requestHash = hashCreateRequest(request);

        IdempotencyRecord existingRecord = idempotencyRecordRepository
                .findByUserIdAndOperationAndIdempotencyKey(actor.getId(), CREATE_EVENT_OPERATION, idempotencyKey)
                .orElse(null);

        if (existingRecord != null) {
            if (!existingRecord.getRequestHash().equals(requestHash)) {
                throw new ConflictException("Idempotency key has already been used with a different payload");
            }
            Event existingEvent = existingRecord.getEvent();
            return new CreateEventResult(EventMapper.toResponse(existingEvent), true);
        }

        Event event = new Event();
        applyEventDetails(event, request);
        event.setSubmittedBy(actor);
        event.setStatus(EventStatus.PENDING);

        Event saved = eventRepository.save(event);

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setOperation(CREATE_EVENT_OPERATION);
        record.setUser(actor);
        record.setEvent(saved);
        record.setRequestHash(requestHash);
        idempotencyRecordRepository.save(record);

        return new CreateEventResult(EventMapper.toResponse(saved), false);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(UUID eventId, Authentication authentication) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.APPROVED && !canAccessNonApproved(authentication, event)) {
            throw new ForbiddenOperationException("You cannot access this event");
        }

        return EventMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> searchEvents(String query,
                                                     EventStatus status,
                                                     Long categoryId,
                                                     OffsetDateTime startFrom,
                                                     OffsetDateTime startTo,
                                                     OffsetDateTime weekStart,
                                                     Pageable pageable,
                                                     Authentication authentication) {
        EventStatus effectiveStatus = resolveStatusFilter(authentication, status);

        Page<EventResponse> page = eventRepository.findAll(
                        EventSpecifications.withFilters(query, effectiveStatus, categoryId, startFrom, startTo, weekStart),
                        pageable)
                .map(EventMapper::toResponse);

        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional
    public EventResponse updateEvent(UUID eventId, EventUpdateRequest request, String username) {
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        User actor = findUser(username);
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isOwner = event.getSubmittedBy().getId().equals(actor.getId());

        if (!isAdmin && !isOwner) {
            throw new ForbiddenOperationException("You can only update your own events");
        }

        if (!isAdmin && event.getStatus() != EventStatus.PENDING) {
            throw new ForbiddenOperationException("Only pending events can be updated by the submitter");
        }

        applyEventDetails(event, request);
        if (!isAdmin) {
            event.setStatus(EventStatus.PENDING);
            event.setReviewedAt(null);
            event.setReviewedBy(null);
            event.setReviewComment(null);
        }

        return EventMapper.toResponse(event);
    }

    @Transactional
    public void deleteEvent(UUID eventId, String username) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        User actor = findUser(username);
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isOwner = event.getSubmittedBy().getId().equals(actor.getId());

        if (!isAdmin && !isOwner) {
            throw new ForbiddenOperationException("You can only delete your own events");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            return;
        }

        event.setStatus(EventStatus.CANCELLED);
    }

    @Transactional
    public EventResponse reviewEvent(UUID eventId, EventReviewRequest request, boolean approve, String adminUsername) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        User admin = findUser(adminUsername);
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenOperationException("Only admins can review events");
        }

        event.setStatus(approve ? EventStatus.APPROVED : EventStatus.REJECTED);
        event.setReviewedBy(admin);
        event.setReviewedAt(OffsetDateTime.now());
        event.setReviewComment(request != null ? request.getComment() : null);

        return EventMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<WeeklyEventMetricResponse> weeklyMetrics(OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("from must be before to");
        }

        return eventRepository.weeklyMetricsRaw(from, to).stream()
                .map(this::toWeeklyMetric)
                .toList();
    }

    private WeeklyEventMetricResponse toWeeklyMetric(Object[] row) {
        OffsetDateTime weekStart;
        if (row[0] instanceof OffsetDateTime value) {
            weekStart = value;
        } else if (row[0] instanceof Timestamp timestamp) {
            weekStart = timestamp.toInstant().atOffset(ZoneOffset.UTC);
        } else if (row[0] instanceof LocalDateTime localDateTime) {
            weekStart = localDateTime.atOffset(ZoneOffset.UTC);
        } else if (row[0] instanceof LocalDate localDate) {
            weekStart = localDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        } else if (row[0] instanceof Instant instant) {
            weekStart = instant.atOffset(ZoneOffset.UTC);
        } else if (row[0] instanceof ZonedDateTime zonedDateTime) {
            weekStart = zonedDateTime.toOffsetDateTime();
        } else {
            throw new BadRequestException("Unsupported week_start type in metric query: "
                    + (row[0] == null ? "null" : row[0].getClass().getName()));
        }

        EventStatus status = EventStatus.valueOf(String.valueOf(row[1]));
        Long total = ((Number) row[2]).longValue();

        return new WeeklyEventMetricResponse(weekStart, status, total);
    }

    private EventStatus resolveStatusFilter(Authentication authentication, EventStatus requestedStatus) {
        boolean admin = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (admin) {
            return requestedStatus;
        }

        if (requestedStatus == null || requestedStatus == EventStatus.APPROVED) {
            return EventStatus.APPROVED;
        }

        throw new ForbiddenOperationException("Only admins can query non-approved events");
    }

    private boolean canAccessNonApproved(Authentication authentication, Event event) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return false;
        }

        User actor = findUser(authentication.getName());
        return actor.getRole() == Role.ADMIN || event.getSubmittedBy().getId().equals(actor.getId());
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private Set<Category> findCategories(Set<Long> categoryIds) {
        List<Category> categories = categoryRepository.findAllById(categoryIds);
        if (categories.size() != categoryIds.size()) {
            throw new BadRequestException("One or more categories do not exist");
        }
        return new HashSet<>(categories);
    }

    private void applyEventDetails(Event event, EventCreateRequest request) {
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription().trim());
        event.setLocation(request.getLocation().trim());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setCapacity(request.getCapacity());
        event.setCategories(findCategories(request.getCategoryIds()));
    }

    private void applyEventDetails(Event event, EventUpdateRequest request) {
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription().trim());
        event.setLocation(request.getLocation().trim());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setCapacity(request.getCapacity());
        event.setCategories(findCategories(request.getCategoryIds()));
    }

    private String hashCreateRequest(EventCreateRequest request) {
        String raw = String.join("|",
                request.getTitle(),
                request.getDescription(),
                request.getLocation(),
                request.getStartTime().toString(),
                request.getEndTime().toString(),
                String.valueOf(request.getCapacity()),
                request.getCategoryIds().stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("")
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BadRequestException("Unable to hash request payload");
        }
    }
}
