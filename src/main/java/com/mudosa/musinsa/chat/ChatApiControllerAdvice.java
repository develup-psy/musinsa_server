package com.mudosa.musinsa.chat;

import com.mudosa.musinsa.chat.controller.ChatController;
import com.mudosa.musinsa.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = {ChatController.class})
public class ChatApiControllerAdvice {

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String paramName = ex.getName(); // 예: chatId
    String invalidValue = ex.getValue() != null ? ex.getValue().toString() : "null";
    String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "Unknown";

    String message = String.format(
        "요청 파라미터 ‘%s’의 값 ‘%s’은(는) 올바르지 않습니다. %s 형식의 값이어야 합니다.",
        paramName, invalidValue, requiredType
    );

    ApiResponse body = ApiResponse.builder()
        .success(false)
        .errorCode("400")
        .message(message)
        .build();

    return ResponseEntity.badRequest().body(body);
  }

}
