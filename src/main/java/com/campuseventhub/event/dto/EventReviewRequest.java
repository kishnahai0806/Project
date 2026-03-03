package com.campuseventhub.event.dto;

import jakarta.validation.constraints.Size;

public class EventReviewRequest {

    @Size(max = 500, message = "Review comment cannot exceed 500 characters")
    private String comment;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
