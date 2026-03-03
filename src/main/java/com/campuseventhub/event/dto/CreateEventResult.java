package com.campuseventhub.event.dto;

public class CreateEventResult {

    private EventResponse event;
    private boolean replayed;

    public CreateEventResult() {
    }

    public CreateEventResult(EventResponse event, boolean replayed) {
        this.event = event;
        this.replayed = replayed;
    }

    public EventResponse getEvent() {
        return event;
    }

    public void setEvent(EventResponse event) {
        this.event = event;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }
}
