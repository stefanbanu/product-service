package com.banu.productservice.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 ProblemDetail bodies, as in the sibling order-service. The class is
 * ApiExceptionHandler and the exceptions extend RuntimeException directly: there is no
 * GlobalExceptionHandler and no BusinessException here.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleNotFound(ProductNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Product not found");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    /**
     * 412, not 409. Both describe a conflict, but the client stated its expectation as an
     * HTTP precondition - {@code If-Match} - so the failure of that precondition has a
     * status of its own. 409 would be the right answer if the version travelled in the
     * request body instead.
     */
    @ExceptionHandler(ProductVersionConflictException.class)
    ProblemDetail handleVersionConflict(ProductVersionConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.PRECONDITION_FAILED);
        problem.setTitle("Precondition failed");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    /**
     * 428 Precondition Required, from RFC 6585, which exists for exactly this: the request
     * was refused for being unconditional, and the client should retry with a precondition.
     * Answering 400 would say the request was malformed, which it was not.
     */
    @ExceptionHandler(IfMatchRequiredException.class)
    ProblemDetail handleIfMatchRequired(IfMatchRequiredException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.PRECONDITION_REQUIRED);
        problem.setTitle("If-Match required");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    /**
     * Spring already answers 400 for a failed {@code @Valid} without this handler. What it
     * adds is the errors map, and it reports EVERY invalid field at once rather than only
     * the first.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request");
        problem.setDetail("Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
