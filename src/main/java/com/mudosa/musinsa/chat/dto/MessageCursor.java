package com.mudosa.musinsa.chat.dto;

import java.time.LocalDateTime;

public record MessageCursor(LocalDateTime createdAt, Long messageId) {

}