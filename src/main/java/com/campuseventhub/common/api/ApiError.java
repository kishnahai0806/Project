package com.campuseventhub.common.api;

import java.time.OffsetDateTime;
import java.util.List;

public class ApiError {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String requestId;
    private List<ValidationError> validationErrors;

    public ApiError() {
    }

    public ApiError(OffsetDateTime timestamp,
                    int status,
                    String error,
                    String message,
                    String path,
                    String requestId,
                    List<ValidationError> validationErrors) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.requestId = requestId;
        this.validationErrors = validationErrors;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
