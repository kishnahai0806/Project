package com.campuseventhub.event.dto;

import com.campuseventhub.event.entity.EventStatus;

import java.time.OffsetDateTime;

public class WeeklyEventMetricResponse {

    private OffsetDateTime weekStart;
    private EventStatus status;
    private Long total;

    public WeeklyEventMetricResponse() {
    }

    public WeeklyEventMetricResponse(OffsetDateTime weekStart, EventStatus status, Long total) {
        this.weekStart = weekStart;
        this.status = status;
        this.total = total;
    }

    public OffsetDateTime getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(OffsetDateTime weekStart) {
        this.weekStart = weekStart;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
