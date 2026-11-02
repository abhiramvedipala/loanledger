package com.loanledger.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** RFC 7807 errors; Spring renders ErrorResponseException as application/problem+json. */
final class Problems {

    private Problems() {
    }

    static ErrorResponseException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, detail);
    }

    static ErrorResponseException conflict(String detail) {
        return of(HttpStatus.CONFLICT, detail);
    }

    static ErrorResponseException unprocessable(String detail) {
        return of(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    private static ErrorResponseException of(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
