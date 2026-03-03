package com.campuseventhub.event.service;

import com.campuseventhub.category.entity.Category;
import com.campuseventhub.category.repository.CategoryRepository;
import com.campuseventhub.common.exception.ConflictException;
import com.campuseventhub.common.exception.ForbiddenOperationException;
import com.campuseventhub.event.dto.CreateEventResult;
import com.campuseventhub.event.dto.EventCreateRequest;
import com.campuseventhub.event.entity.Event;
import com.campuseventhub.event.entity.EventStatus;
import com.campuseventhub.event.repository.EventRepository;
import com.campuseventhub.idempotency.entity.IdempotencyRecord;
import com.campuseventhub.idempotency.repository.IdempotencyRecordRepository;
import com.campuseventhub.user.entity.Role;
import com.campuseventhub.user.entity.User;
import com.campuseventhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @InjectMocks
    private EventService eventService;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("student1");
        user.setRole(Role.USER);

        category = new Category();
        category.setId(10L);
        category.setName("Tech Talks");
    }

    @Test
    void createEventCreatesNewRecordWhenIdempotencyKeyNotSeen() {
        EventCreateRequest request = validRequest("New Event");

        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));
        when(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(1L, "CREATE_EVENT", "idem-1"))
                .thenReturn(Optional.empty());
        when(categoryRepository.findAllById(Set.of(10L))).thenReturn(List.of(category));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0, Event.class);
            e.setId(UUID.randomUUID());
            return e;
        });

        CreateEventResult result = eventService.createEvent(request, "student1", "idem-1");

        assertThat(result.isReplayed()).isFalse();
        assertThat(result.getEvent().getTitle()).isEqualTo("New Event");
        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    void createEventReturnsExistingWhenIdempotencyKeyReplayedWithSamePayload() {
        EventCreateRequest request = validRequest("Existing Event");

        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));
        when(categoryRepository.findAllById(Set.of(10L))).thenReturn(List.of(category));

        AtomicReference<IdempotencyRecord> storedRecord = new AtomicReference<>();
        when(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(1L, "CREATE_EVENT", "idem-1"))
                .thenAnswer(invocation -> Optional.ofNullable(storedRecord.get()));
        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> {
                    IdempotencyRecord record = invocation.getArgument(0, IdempotencyRecord.class);
                    storedRecord.set(record);
                    return record;
                });
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0, Event.class);
            e.setId(UUID.randomUUID());
            return e;
        });

        CreateEventResult first = eventService.createEvent(request, "student1", "idem-1");
        CreateEventResult second = eventService.createEvent(request, "student1", "idem-1");

        assertThat(first.isReplayed()).isFalse();
        assertThat(second.isReplayed()).isTrue();
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void createEventThrowsWhenIdempotencyKeyPayloadDoesNotMatch() {
        EventCreateRequest requestA = validRequest("Event A");
        EventCreateRequest requestB = validRequest("Event B");

        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));
        when(categoryRepository.findAllById(Set.of(10L))).thenReturn(List.of(category));

        AtomicReference<IdempotencyRecord> storedRecord = new AtomicReference<>();
        when(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(1L, "CREATE_EVENT", "idem-x"))
                .thenAnswer(invocation -> Optional.ofNullable(storedRecord.get()));
        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> {
                    IdempotencyRecord record = invocation.getArgument(0, IdempotencyRecord.class);
                    storedRecord.set(record);
                    return record;
                });
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0, Event.class);
            e.setId(UUID.randomUUID());
            return e;
        });

        eventService.createEvent(requestA, "student1", "idem-x");

        assertThatThrownBy(() -> eventService.createEvent(requestB, "student1", "idem-x"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Idempotency key");

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void searchEventsRejectsNonAdminForNonApprovedStatus() {
        TestingAuthenticationToken userAuth = new TestingAuthenticationToken("student1", null, "ROLE_USER");

        assertThatThrownBy(() -> eventService.searchEvents(
                null,
                EventStatus.PENDING,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10),
                userAuth))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void searchEventsAllowsAdminStatusFilter() {
        TestingAuthenticationToken adminAuth = new TestingAuthenticationToken("admin", null, "ROLE_ADMIN");
        when(eventRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Event>>any(), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of()));

        var result = eventService.searchEvents(
                null,
                EventStatus.PENDING,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10),
                adminAuth);

        assertThat(result.getContent()).isEmpty();
    }

    private EventCreateRequest validRequest(String title) {
        EventCreateRequest request = new EventCreateRequest();
        request.setTitle(title);
        request.setDescription("Description");
        request.setLocation("Campus Hall");
        request.setStartTime(OffsetDateTime.now().plusDays(2));
        request.setEndTime(OffsetDateTime.now().plusDays(2).plusHours(2));
        request.setCapacity(100);
        request.setCategoryIds(Set.of(10L));
        return request;
    }
}
