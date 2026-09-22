package com.ariadne.presentation;

import com.ariadne.extraction.BadRequestException;
import com.ariadne.extraction.FundNotFoundException;
import com.ariadne.extraction.UpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse badRequest(BadRequestException exception) { return new ErrorResponse(exception.getMessage()); }

    @ExceptionHandler(FundNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound(FundNotFoundException exception) { return new ErrorResponse(exception.getMessage()); }

    @ExceptionHandler(UpstreamException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ErrorResponse upstream() { return new ErrorResponse("upstream unavailable"); }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErrorResponse database() { return new ErrorResponse("database unavailable"); }

    record ErrorResponse(String message) {}
}
