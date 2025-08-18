package com.paynow.agentassist.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success, T data, ApiError error, String message, Instant timestamp) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, null, Instant.now());
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, null, message, Instant.now());
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return new ApiResponse<>(false, null, new ApiError(code, message), null, Instant.now());
  }

  public static <T> ApiResponse<T> error(String code, String message, String details) {
    return new ApiResponse<>(
        false, null, new ApiError(code, message, details), null, Instant.now());
  }

  public record ApiError(String code, String message, String details) {
    public ApiError(String code, String message) {
      this(code, message, null);
    }
  }
}
