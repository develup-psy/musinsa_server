package com.mudosa.musinsa.chat.file;

import com.mudosa.musinsa.chat.event.TempUploadedFile;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class AbstractS3Store {

  protected final Tracer tracer;

  @Value("${aws.s3.bucket}")
  protected String bucketName;

  // 1. 채팅 메시지용 키 생성
  protected String generateMessageKey(Long chatId, Long messageId, TempUploadedFile file) {
    String original = Objects.requireNonNullElse(file.originalFilename(), "unknown");
    String safeName = UUID.randomUUID() + "_" + StringUtils.cleanPath(original);

    // chat/{chatId}/message/{messageId}/{uuid_filename}
    return String.format("chat/%d/message/%d/%s", chatId, messageId, safeName);
  }

  // 2. 브랜드 로고용 키 생성
  protected String generateBrandKey(Long brandId, MultipartFile file) {
    String original = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown");
    String safeName = UUID.randomUUID() + "_" + StringUtils.cleanPath(original);

    // brand/{brandId}/logo/{uuid_filename}
    return String.format("brand/%d/logo/%s", brandId, safeName);
  }
}