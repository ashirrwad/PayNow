package com.paynow.agentassist.exception;

import com.paynow.agentassist.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {

    String validationErrors = ex.getBindingResult()
        .getAllErrors()
        .stream()
        .map(error -> {
          String fieldName = ((FieldError) error).getField();
          String errorMessage = error.getDefaultMessage();
          return fieldName + ": " + errorMessage;
        })
        .collect(Collectors.joining(", "));

    logger.warn("Validation error: {}", validationErrors);

    ApiResponse<Void> response = ApiResponse.error(
        "VALIDATION_ERROR", 
        "Request validation failed", 
        validationErrors
    );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException ex) {

    logger.warn("Invalid argument: {}", ex.getMessage());

    ApiResponse<Void> response = ApiResponse.error(
        "INVALID_REQUEST", 
        "Invalid request parameter", 
        ex.getMessage()
    );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    logger.error("Unexpected error", ex);

    ApiResponse<Void> response = ApiResponse.error(
        "INTERNAL_ERROR", 
        "An unexpected error occurred"
    );

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(
      HttpRequestMethodNotSupportedException ex) {

    logger.warn("Method not allowed: {}", ex.getMessage());

    ApiResponse<Void> response = ApiResponse.error(
        "METHOD_NOT_ALLOWED", 
        "HTTP method not supported", 
        ex.getMessage()
    );

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException ex) {

    logger.warn("Malformed request body: {}", ex.getMessage());

    ApiResponse<Void> response = ApiResponse.error(
        "MALFORMED_REQUEST", 
        "Request body is malformed or missing", 
        "Please provide a valid JSON request body"
    );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
      HttpMediaTypeNotSupportedException ex) {

    logger.warn("Unsupported media type: {}", ex.getMessage());

    ApiResponse<Void> response = ApiResponse.error(
        "UNSUPPORTED_MEDIA_TYPE", 
        "Content type not supported", 
        "Please use application/json content type"
    );

    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
  }
}
