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
