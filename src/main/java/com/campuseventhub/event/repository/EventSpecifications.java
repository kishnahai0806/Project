package com.campuseventhub.event.repository;

import com.campuseventhub.event.entity.Event;
import com.campuseventhub.event.entity.EventStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> withFilters(String query,
                                                   EventStatus status,
                                                   Long categoryId,
                                                   OffsetDateTime startFrom,
                                                   OffsetDateTime startTo,
                                                   OffsetDateTime weekStart) {
        return (root, cq, cb) -> {
            cq.distinct(true);
            var predicate = cb.conjunction();

            if (query != null && !query.isBlank()) {
                String wildcard = "%" + query.toLowerCase() + "%";
                predicate = cb.and(predicate,
                        cb.or(
                                cb.like(cb.lower(root.get("title")), wildcard),
                                cb.like(cb.lower(root.get("description")), wildcard),
                                cb.like(cb.lower(root.get("location")), wildcard)
                        )
                );
            }

            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }

            if (categoryId != null) {
                predicate = cb.and(predicate, cb.equal(root.join("categories").get("id"), categoryId));
            }

            if (startFrom != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("startTime"), startFrom));
            }

            if (startTo != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("startTime"), startTo));
            }

            if (weekStart != null) {
                OffsetDateTime weekEnd = weekStart.plusDays(7);
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(root.get("startTime"), weekStart),
                        cb.lessThan(root.get("startTime"), weekEnd));
            }

            return predicate;
        };
    }
}
