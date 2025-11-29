package com.mudosa.musinsa.common.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApiResponse<T> {

  private boolean success;
  private T data;
  private String errorCode;
  private String message;
  private LocalDateTime timestamp;

  public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
  }

  public static <T> ApiResponse<T> success(T data) {
    return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
  }

  public static <T> ApiResponse<T> failure(String errorCode, String message) {
    return ApiResponse.<T>builder()
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
  }

  public static <T> ApiResponse<T> failure(String errorCode, String message, T data) {
    return ApiResponse.<T>builder()
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
  }
}
