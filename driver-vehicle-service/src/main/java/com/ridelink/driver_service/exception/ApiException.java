package com.ridelink.driver_service.exception;

import org.springframework.http.HttpStatus;

/** Base type for every business error that maps cleanly onto an HTTP status. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}